package com.smartprint.direct.hp

import android.util.Log
import com.smartprint.direct.model.InkCartridge
import com.smartprint.direct.model.PrinterState
import com.smartprint.direct.network.DualNetworkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.regex.Pattern

data class HpDeviceDetails(
    val makeAndModel: String = "",
    val serialNumber: String = "Unknown",
    val firmwareVersion: String = "Unknown",
    val totalImpressions: Int = 0,
    val colorImpressions: Int = 0,
    val monoImpressions: Int = 0,
    val scannerStatus: String = "Ready"
)

/**
 * Authentic HP Protocols extracted directly from HP Smart:
 * - /DevMgmt/ProductStatusDyn.xml
 * - /DevMgmt/ProductConfigDyn.xml
 * - /DevMgmt/ConsumableConfigDyn.xml
 * - /DevMgmt/ProductUsageDyn.xml
 * - /eSCL/ScannerStatus
 */
class HpAuthenticProtocol(private val router: DualNetworkRouter) {

    companion object {
        const val PATH_PRODUCT_STATUS = "/DevMgmt/ProductStatusDyn.xml"
        const val PATH_PRODUCT_CONFIG = "/DevMgmt/ProductConfigDyn.xml"
        const val PATH_CONSUMABLE_CONFIG = "/DevMgmt/ConsumableConfigDyn.xml"
        const val PATH_PRODUCT_USAGE = "/DevMgmt/ProductUsageDyn.xml"
        const val PATH_ESCL_SCANNER_STATUS = "/eSCL/ScannerStatus"
        const val PATH_IPP_PRINT = "/ipp/print"
    }

    /**
     * Queries HP /DevMgmt/ProductStatusDyn.xml for real-time printer state
     */
    suspend fun queryDeviceStatus(ipAddress: String): PrinterState = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress$PATH_PRODUCT_STATUS")
            val conn = router.openWifiConnection(url)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val text = conn.getInputStream().bufferedReader().use { it.readText() }

            val tokens = Regex("<(?:\\w+:)?(?:StatusCategory|AlertCategory|AlertDetailsCategory|AlertID)>([^<]+)<")
                .findAll(text).map { it.groupValues[1].lowercase() }.toList()
            fun has(vararg k: String) = tokens.any { t -> k.any { t.contains(it) } }
            when {
                has("jam") -> PrinterState.PAPER_JAM
                has("outofpaper", "mediaempty", "trayempty", "inputtrayempty", "nopaper") -> PrinterState.OUT_OF_PAPER
                has("dooropen", "coveropen", "doorcover") -> PrinterState.DOOR_OPEN
                has("processing", "printing", "busy", "warmingup") -> PrinterState.PRINTING
                else -> PrinterState.IDLE_READY
            }
        } catch (e: Exception) {
            Log.w("HpProtocol", "Could not query ProductStatusDyn.xml: ${e.message}")
            PrinterState.OFFLINE
        }
    }

    /**
     * Queries HP /DevMgmt/ConsumableConfigDyn.xml for exact CMYK tank percentages
     */
    suspend fun queryConsumableLevels(ipAddress: String): List<InkCartridge> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress$PATH_CONSUMABLE_CONFIG")
            val conn = router.openWifiConnection(url)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            HpInkStatusParser.parseEwsXml(conn.getInputStream())
        } catch (e: Exception) {
            // Fallback to ProductStatusCap
            try {
                val url = URL("http://$ipAddress/DevMgmt/ProductStatusCap.xml")
                val conn = router.openWifiConnection(url)
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                HpInkStatusParser.parseEwsXml(conn.getInputStream())
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Queries HP /DevMgmt/ProductConfigDyn.xml and /DevMgmt/ProductUsageDyn.xml
     */
    suspend fun queryDeviceDetails(ipAddress: String): HpDeviceDetails? = withContext(Dispatchers.IO) {
        var gotAnything = false
        var model = ""
        var serial = "Unknown"
        var fw = "Unknown"
        var totalPages = 0
        var colorPages = 0
        var monoPages = 0
        var scanStatus = "Ready"

        // 1. Config
        try {
            val url = URL("http://$ipAddress$PATH_PRODUCT_CONFIG")
            val conn = router.openWifiConnection(url)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val text = conn.getInputStream().bufferedReader().use { it.readText() }

            gotAnything = true
            val modelMatcher = Pattern.compile("<(?:\\w+:)?MakeAndModel>(.*?)</(?:\\w+:)?MakeAndModel>").matcher(text)
            if (modelMatcher.find()) model = modelMatcher.group(1)?.trim() ?: model

            val serialMatcher = Pattern.compile("<(?:\\w+:)?SerialNumber>(.*?)</(?:\\w+:)?SerialNumber>").matcher(text)
            if (serialMatcher.find()) serial = serialMatcher.group(1)?.trim() ?: serial

            val fwMatcher = Pattern.compile("<(?:\\w+:)?FirmwareVersion>(.*?)</(?:\\w+:)?FirmwareVersion>").matcher(text)
            if (fwMatcher.find()) fw = fwMatcher.group(1)?.trim() ?: fw
        } catch (_: Exception) {}

        // 2. Usage
        try {
            val url = URL("http://$ipAddress$PATH_PRODUCT_USAGE")
            val conn = router.openWifiConnection(url)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val text = conn.getInputStream().bufferedReader().use { it.readText() }

            gotAnything = true
            val totalM = Pattern.compile("<(?:\\w+:)?TotalImpressions>(\\d+)</(?:\\w+:)?TotalImpressions>").matcher(text)
            if (totalM.find()) totalPages = totalM.group(1)?.toIntOrNull() ?: 0

            val colorM = Pattern.compile("<(?:\\w+:)?ColorImpressions>(\\d+)</(?:\\w+:)?ColorImpressions>").matcher(text)
            if (colorM.find()) colorPages = colorM.group(1)?.toIntOrNull() ?: 0

            val monoM = Pattern.compile("<(?:\\w+:)?MonochromeImpressions>(\\d+)</(?:\\w+:)?MonochromeImpressions>").matcher(text)
            if (monoM.find()) monoPages = monoM.group(1)?.toIntOrNull() ?: 0
        } catch (_: Exception) {}

        // 3. eSCL Scanner
        try {
            val url = URL("http://$ipAddress$PATH_ESCL_SCANNER_STATUS")
            val conn = router.openWifiConnection(url)
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val text = conn.getInputStream().bufferedReader().use { it.readText() }
            if (text.contains("<pwg:State>Idle</pwg:State>")) scanStatus = "Scanner Idle & Ready"
            else if (text.contains("<pwg:State>Processing</pwg:State>")) scanStatus = "Scanning in Progress"
        } catch (_: Exception) {}

        if (!gotAnything) return@withContext null

        HpDeviceDetails(
            makeAndModel = model,
            serialNumber = serial,
            firmwareVersion = fw,
            totalImpressions = totalPages,
            colorImpressions = colorPages,
            monoImpressions = monoPages,
            scannerStatus = scanStatus
        )
    }
}
