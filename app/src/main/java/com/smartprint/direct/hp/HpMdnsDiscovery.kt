package com.smartprint.direct.hp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.smartprint.direct.model.ConnectionType
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.model.PrintProtocol
import com.smartprint.direct.model.PrinterBrand
import com.smartprint.direct.model.PrinterState
import com.smartprint.direct.network.DualNetworkRouter
import com.smartprint.direct.protocol.UniversalSnmpClient
import com.smartprint.direct.usb.UsbPrinterManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Multi-brand discovery.
 * Fixed vs. the previous version:
 *  - printers list was read-modify-written from several IO threads (lost updates / duplicates) -> atomic update
 *  - the same host advertised via IPP/RAW/LPR/BJNP overwrote itself in resolve order, so the chosen
 *    protocol was random -> deterministic priority per host
 *  - "Scanning..." never stopped -> scan window with auto stop
 *  - fake ink levels were attached to every printer -> removed (unknown stays empty)
 *  - brand detection matched "hp" inside any word; SNMP socket leaked; coroutine scope never cancelled
 */
class HpMdnsDiscovery(
    private val context: Context,
    private val router: DualNetworkRouter
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val usbManager = UsbPrinterManager(context)

    private val _printers = MutableStateFlow<List<HpPrinter>>(emptyList())
    val printers: StateFlow<List<HpPrinter>> = _printers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private val activeListeners = CopyOnWriteArrayList<NsdManager.DiscoveryListener>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val resolveChannel = Channel<Pair<NsdServiceInfo, PrintProtocol>>(Channel.UNLIMITED)
    private var resolveJob: Job? = null
    private var scanTimeoutJob: Job? = null

    private val hpRegex = Regex("\\b(hp|hewlett|deskjet|laserjet|officejet|photosmart|envy|neverstop)\\b", RegexOption.IGNORE_CASE)

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true
        startResolveWorker()

        try {
            usbManager.findConnectedUsbPrinters().forEach { upsert(it) }
        } catch (e: Exception) {
            Log.w("UniversalDiscovery", "USB scan warning: ${e.message}")
        }

        try {
            multicastLock = wifiManager?.createMulticastLock("UniversalMdnsLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}

        val serviceTypes = listOf(
            "_ipp._tcp" to PrintProtocol.IPP_EVERYWHERE,
            "_pdl-datastream._tcp" to PrintProtocol.RAW_9100,
            "_canon-bjnp1._tcp" to PrintProtocol.CANON_BJNP,
            "_printer._tcp" to PrintProtocol.LPR_515
        )

        serviceTypes.forEach { (sType, defaultProtocol) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveChannel.trySend(serviceInfo to defaultProtocol)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val lost = serviceInfo.serviceName
                    _printers.update { list -> list.filterNot { it.connectionType == ConnectionType.WIFI_NETWORK && it.name == lost } }
                }

                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w("UniversalDiscovery", "Start discovery failed $serviceType: $errorCode")
                    activeListeners.remove(this)
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            }
            try {
                nsdManager.discoverServices(sType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeListeners.add(listener)
            } catch (e: Exception) {
                Log.w("UniversalDiscovery", "Could not start discovery for $sType: ${e.message}")
            }
        }

        // Scan window: stop after 20s so the UI does not say "Scanning..." forever and the
        // multicast lock / NSD listeners do not drain the battery. Refresh re-scans.
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(20_000)
            stopDiscovery()
        }
    }

    private fun startResolveWorker() {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            for ((serviceInfo, defaultProtocol) in resolveChannel) {
                resolveSingleService(serviceInfo, defaultProtocol)
            }
        }
    }

    private suspend fun resolveSingleService(serviceInfo: NsdServiceInfo, protocol: PrintProtocol) {
        val completer = CompletableDeferred<Unit>()
        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w("UniversalDiscovery", "Resolve failed ($errorCode) for ${serviceInfo.serviceName}")
                    completer.complete(Unit)
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    handleResolvedService(resolved, protocol)
                    completer.complete(Unit)
                }
            })
            withTimeoutOrNull(3500) { completer.await() }
        } catch (e: Exception) {
            Log.e("UniversalDiscovery", "Resolution exception", e)
        }
    }

    private fun handleResolvedService(resolved: NsdServiceInfo, protocol: PrintProtocol) {
        val hostAddress = extractIPv4Address(resolved) ?: return
        val port = if (resolved.port > 0) resolved.port else protocol.defaultPort
        val serviceName = resolved.serviceName ?: "Network Printer"

        scope.launch {
            val lower = serviceName.lowercase()
            val brand = when {
                "canon" in lower -> PrinterBrand.CANON
                "epson" in lower -> PrinterBrand.EPSON
                "brother" in lower -> PrinterBrand.BROTHER
                hpRegex.containsMatchIn(serviceName) -> PrinterBrand.HP
                "samsung" in lower -> PrinterBrand.SAMSUNG
                "pantum" in lower -> PrinterBrand.PANTUM
                "ricoh" in lower -> PrinterBrand.RICOH
                "xerox" in lower -> PrinterBrand.XEROX
                else -> UniversalSnmpClient.identifyPrinter(hostAddress, network = router.getActiveWifiNetwork())?.brand
                    ?: PrinterBrand.GENERIC
            }

            upsert(
                HpPrinter(
                    id = hostAddress,
                    name = serviceName,
                    model = "${brand.displayName} Network Printer",
                    brand = brand,
                    protocol = protocol,
                    ipAddress = hostAddress,
                    port = port,
                    ippPort = if (protocol == PrintProtocol.IPP_EVERYWHERE) port else 631,
                    isOnline = true,
                    state = PrinterState.IDLE_READY,
                    connectionType = ConnectionType.WIFI_NETWORK
                )
            )
        }
    }

    /** Higher = more reliably printable by this app's engine. */
    private fun rank(p: PrintProtocol) = when (p) {
        PrintProtocol.USB_OTG -> 5
        PrintProtocol.RAW_9100 -> 4
        PrintProtocol.LPR_515 -> 3
        PrintProtocol.IPP_EVERYWHERE -> 2
        PrintProtocol.CANON_BJNP -> 1
    }

    /** Atomic, per-host merge: the best protocol wins no matter which service resolves first. */
    private fun upsert(printer: HpPrinter, force: Boolean = false) {
        _printers.update { current ->
            val i = current.indexOfFirst { it.id == printer.id }
            when {
                i < 0 -> current + printer
                force || rank(printer.protocol) >= rank(current[i].protocol) ->
                    current.toMutableList().also {
                        it[i] = printer.copy(inkLevels = printer.inkLevels.ifEmpty { current[i].inkLevels })
                    }
                else -> current
            }
        }
    }

    /** For manually entered printers so they also show up in the picker. */
    fun addManual(printer: HpPrinter) = upsert(printer, force = true)

    private fun extractIPv4Address(info: NsdServiceInfo): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                for (addr in info.hostAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (_: Throwable) {}

        @Suppress("DEPRECATION")
        val legacy = info.host
        return if (legacy is Inet4Address && !legacy.isLoopbackAddress) legacy.hostAddress else null
    }

    fun stopDiscovery() {
        scanTimeoutJob?.cancel()
        resolveJob?.cancel()
        activeListeners.forEach { listener ->
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        activeListeners.clear()
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
        _isDiscovering.value = false
    }

    /** Final teardown (ViewModel.onCleared). */
    fun close() {
        stopDiscovery()
        scope.cancel()
    }
}
