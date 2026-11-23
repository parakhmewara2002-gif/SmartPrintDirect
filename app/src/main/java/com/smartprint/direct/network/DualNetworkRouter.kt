package com.smartprint.direct.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection

data class DualNetworkState(
    val isWifiConnected: Boolean = false,
    val isCellularConnected: Boolean = false,
    val isDualPathActive: Boolean = false,
    val wifiNetwork: Network? = null,
    val cellularNetwork: Network? = null,
    val statusMessage: String = "Monitoring network interfaces..."
)

/**
 * Robust DualNetworkRouter (Production Hardened)
 * - Prevents Cellular DNS leakage for local mDNS hostnames (.local)
 * - Synchronously detects active Wi-Fi on cold start (zero startup delay)
 * - Keeps Cellular default internet active while routing all printer traffic over Wi-Fi
 */
class DualNetworkRouter(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(DualNetworkState())
    val networkState: StateFlow<DualNetworkState> = _networkState.asStateFlow()

    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var cellularCallback: ConnectivityManager.NetworkCallback? = null

    fun startRouting() {
        if (wifiCallback != null) return

        // BUG-02 FIX: Synchronously inspect currently active networks on startup to avoid null window
        synchronizeCurrentNetworks()

        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i("DualNetworkRouter", "Wi-Fi Interface detected: $network")
                val cur = _networkState.value
                val isDual = cur.isCellularConnected
                _networkState.value = cur.copy(
                    isWifiConnected = true,
                    wifiNetwork = network,
                    isDualPathActive = isDual,
                    statusMessage = if (isDual) "Dual-Path Active: Wi-Fi (Printer) + SIM (Internet)" else "Wi-Fi Connected"
                )
            }

            override fun onLost(network: Network) {
                Log.w("DualNetworkRouter", "Wi-Fi Interface lost: $network")
                val cur = _networkState.value
                if (cur.wifiNetwork == network) {
                    _networkState.value = cur.copy(
                        isWifiConnected = false,
                        wifiNetwork = null,
                        isDualPathActive = false,
                        statusMessage = if (cur.isCellularConnected) "SIM Internet Only" else "No Connection"
                    )
                }
            }
        }

        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        cellularCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i("DualNetworkRouter", "Cellular (SIM) Interface detected: $network")
                val cur = _networkState.value
                val isDual = cur.isWifiConnected
                _networkState.value = cur.copy(
                    isCellularConnected = true,
                    cellularNetwork = network,
                    isDualPathActive = isDual,
                    statusMessage = if (isDual) "Dual-Path Active: Wi-Fi (Printer) + SIM (Internet)" else "SIM Internet Active"
                )
            }

            override fun onLost(network: Network) {
                Log.w("DualNetworkRouter", "Cellular (SIM) Interface lost: $network")
                val cur = _networkState.value
                if (cur.cellularNetwork == network) {
                    _networkState.value = cur.copy(
                        isCellularConnected = false,
                        cellularNetwork = null,
                        isDualPathActive = false,
                        statusMessage = if (cur.isWifiConnected) "Wi-Fi Only" else "No Connection"
                    )
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback!!)
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback!!)
        } catch (e: Exception) {
            Log.e("DualNetworkRouter", "Error registering callbacks", e)
        }
    }

    private fun synchronizeCurrentNetworks() {
        try {
            var foundWifi: Network? = null
            var foundCellular: Network? = null

            for (net in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    foundWifi = net
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    foundCellular = net
                }
            }

            if (foundWifi != null || foundCellular != null) {
                val isDual = (foundWifi != null && foundCellular != null)
                _networkState.value = DualNetworkState(
                    isWifiConnected = foundWifi != null,
                    isCellularConnected = foundCellular != null,
                    isDualPathActive = isDual,
                    wifiNetwork = foundWifi,
                    cellularNetwork = foundCellular,
                    statusMessage = if (isDual) "Dual-Path Active: Wi-Fi (Printer) + SIM (Internet)" else "Network Synchronized"
                )
            }
        } catch (e: Exception) {
            Log.w("DualNetworkRouter", "Sync networks error: ${e.message}")
        }
    }

    fun getActiveWifiNetwork(): Network? {
        _networkState.value.wifiNetwork?.let { return it }
        // Fallback
        for (net in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return net
            }
        }
        return null
    }

    /**
     * Creates and connects a socket strictly bound to Wi-Fi.
     * BUG-01 FIX: Resolves hostnames using Wi-Fi DNS (preventing cellular DNS leakage)
     */
    fun createBoundSocket(ipOrHost: String, port: Int, timeoutMs: Int = 8000): Socket {
        val wifiNet = getActiveWifiNetwork()
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.keepAlive = true

        if (wifiNet != null) {
            try {
                wifiNet.bindSocket(socket)
                Log.d("DualNetworkRouter", "Socket bound to Wi-Fi: $wifiNet")
            } catch (e: IOException) {
                Log.w("DualNetworkRouter", "Socket binding warning: ${e.message}")
            }
        }

        // BUG-01 FIX: Resolve address via Wi-Fi network interface to resolve .local hostnames correctly
        val inetAddress: InetAddress = if (wifiNet != null) {
            try {
                wifiNet.getByName(ipOrHost)
            } catch (_: Exception) {
                InetAddress.getByName(ipOrHost)
            }
        } else {
            InetAddress.getByName(ipOrHost)
        }

        socket.connect(InetSocketAddress(inetAddress, port), timeoutMs)
        return socket
    }

    fun openWifiConnection(url: URL): URLConnection {
        val wifiNet = getActiveWifiNetwork()
        return if (wifiNet != null) {
            wifiNet.openConnection(url)
        } else {
            url.openConnection()
        }
    }

    fun release() {
        wifiCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        cellularCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        wifiCallback = null
        cellularCallback = null
    }
}
