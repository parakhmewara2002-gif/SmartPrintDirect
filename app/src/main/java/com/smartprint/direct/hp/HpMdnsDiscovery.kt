package com.smartprint.direct.hp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.smartprint.direct.model.ConnectionType
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.model.PrinterBrand
import com.smartprint.direct.model.PrintProtocol
import com.smartprint.direct.model.PrinterState
import com.smartprint.direct.network.DualNetworkRouter
import com.smartprint.direct.protocol.UniversalSnmpClient
import com.smartprint.direct.usb.UsbPrinterManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

/**
 * Universal Multi-Brand Discovery Engine (Production Hardened)
 * - BUG-03 FIX: Serial resolution queue prevents NsdManager FAILURE_ALREADY_ACTIVE (3) crashes
 * - BUG-04 FIX: Strictly prefers IPv4 over unroutable link-local IPv6 addresses
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
    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // BUG-03 FIX: Serial Queue for NSD resolution requests
    private val resolveChannel = Channel<Pair<NsdServiceInfo, PrintProtocol>>(Channel.UNLIMITED)
    private var resolveJob: Job? = null

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        startResolveWorker()

        // 1. Scan for USB OTG connected printers
        try {
            val usbPrinters = usbManager.findConnectedUsbPrinters()
            if (usbPrinters.isNotEmpty()) {
                val list = _printers.value.toMutableList()
                usbPrinters.forEach { usb ->
                    if (list.none { it.id == usb.id }) list.add(0, usb)
                }
                _printers.value = list
            }
        } catch (e: Exception) {
            Log.w("UniversalDiscovery", "USB scan warning: ${e.message}")
        }

        // 2. Acquire Wi-Fi Multicast Lock
        try {
            multicastLock = wifiManager?.createMulticastLock("UniversalMdnsLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        val serviceTypes = listOf(
            Pair("_ipp._tcp", PrintProtocol.IPP_EVERYWHERE),
            Pair("_pdl-datastream._tcp", PrintProtocol.RAW_9100),
            Pair("_canon-bjnp1._tcp", PrintProtocol.CANON_BJNP),
            Pair("_printer._tcp", PrintProtocol.LPR_515)
        )

        serviceTypes.forEach { (sType, defaultProtocol) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // BUG-03 FIX: Enqueue service instead of immediately calling nsdManager.resolveService
                    resolveChannel.trySend(Pair(serviceInfo, defaultProtocol))
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val current = _printers.value.toMutableList()
                    current.removeAll { it.name == serviceInfo.serviceName }
                    _printers.value = current
                }

                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            }

            try {
                nsdManager.discoverServices(sType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeListeners.add(listener)
            } catch (e: Exception) {
                Log.w("UniversalDiscovery", "Could not start discovery for $sType: ${e.message}")
            }
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

    private suspend fun resolveSingleService(serviceInfo: NsdServiceInfo, protocol: PrintProtocol) = withContext(Dispatchers.IO) {
        val completer = CompletableDeferred<Unit>()
        try {
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
            // Wait for resolution with a 3.5s timeout per printer so queue never hangs
            withTimeoutOrNull(3500) { completer.await() }
        } catch (e: Exception) {
            Log.e("UniversalDiscovery", "Resolution exception", e)
        }
    }

    private fun handleResolvedService(resolved: NsdServiceInfo, protocol: PrintProtocol) {
        // BUG-04 FIX: Strictly prefer IPv4 address to avoid ENETUNREACH on link-local IPv6
        val hostAddress = extractIPv4Address(resolved) ?: return
        val port = if (resolved.port > 0) resolved.port else protocol.defaultPort
        val serviceName = resolved.serviceName ?: "Network Printer"

        scope.launch {
            val brand = when {
                serviceName.contains("canon", ignoreCase = true) -> PrinterBrand.CANON
                serviceName.contains("epson", ignoreCase = true) -> PrinterBrand.EPSON
                serviceName.contains("brother", ignoreCase = true) -> PrinterBrand.BROTHER
                serviceName.contains("hp", ignoreCase = true) || serviceName.contains("deskjet", ignoreCase = true) || serviceName.contains("laserjet", ignoreCase = true) -> PrinterBrand.HP
                serviceName.contains("samsung", ignoreCase = true) -> PrinterBrand.SAMSUNG
                serviceName.contains("pantum", ignoreCase = true) -> PrinterBrand.PANTUM
                serviceName.contains("ricoh", ignoreCase = true) -> PrinterBrand.RICOH
                serviceName.contains("xerox", ignoreCase = true) -> PrinterBrand.XEROX
                else -> {
                    val snmpId = UniversalSnmpClient.identifyPrinter(hostAddress)
                    snmpId?.brand ?: PrinterBrand.GENERIC
                }
            }

            val inkLevels = if (brand == PrinterBrand.HP) {
                try {
                    val ewsUrl = URL("http://$hostAddress/DevMgmt/ProductStatusCap.xml")
                    val conn = router.openWifiConnection(ewsUrl)
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    HpInkStatusParser.parseEwsXml(conn.getInputStream())
                } catch (_: Exception) {
                    UniversalSnmpClient.defaultBrandTanks(brand)
                }
            } else {
                UniversalSnmpClient.defaultBrandTanks(brand)
            }

            val printer = HpPrinter(
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
                connectionType = ConnectionType.WIFI_NETWORK,
                inkLevels = inkLevels
            )

            val list = _printers.value.toMutableList()
            val existing = list.indexOfFirst { it.id == hostAddress }
            if (existing >= 0) {
                list[existing] = printer
            } else {
                list.add(printer)
            }
            _printers.value = list
        }
    }

    private fun extractIPv4Address(info: NsdServiceInfo): String? {
        // In Android 12+ (API 34), info.hostAddresses can contain multiple addresses
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                for (addr in info.hostAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Throwable) {}

        @Suppress("DEPRECATION")
        val legacyHost = info.host
        if (legacyHost is Inet4Address && !legacyHost.isLoopbackAddress) {
            return legacyHost.hostAddress
        }
        return legacyHost?.hostAddress
    }

    fun stopDiscovery() {
        resolveJob?.cancel()
        activeListeners.forEach { listener ->
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        activeListeners.clear()
        try { multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
        _isDiscovering.value = false
    }
}
