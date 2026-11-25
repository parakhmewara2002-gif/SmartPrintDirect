package com.smartprint.direct.protocol

import android.util.Log
import com.smartprint.direct.model.InkCartridge
import com.smartprint.direct.model.PrinterBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class SnmpPrinterIdentification(
    val brand: PrinterBrand,
    val modelName: String,
    val isPrinter: Boolean = true
)

object UniversalSnmpClient {

    /**
     * Standard SNMP v1 GetRequest packet for OID 1.3.6.1.2.1.1.1.0 (sysDescr)
     * Community: "public"
     */
    private val SNMP_GET_SYSDESCR = byteArrayOf(
        0x30, 0x29, 0x02, 0x01, 0x00, 0x04, 0x06, 0x70, 0x75, 0x62, 0x6c, 0x69, 0x63,
        0xa0.toByte(), 0x1c, 0x02, 0x04, 0x00, 0x00, 0x00, 0x01, 0x02, 0x01, 0x00, 0x02,
        0x01, 0x00, 0x30, 0x0e, 0x30, 0x0c, 0x06, 0x08, 0x2b, 0x06, 0x01, 0x02, 0x01,
        0x01, 0x01, 0x00, 0x05, 0x00
    )

    suspend fun identifyPrinter(
        ipAddress: String,
        timeoutMs: Int = 2000,
        network: android.net.Network? = null
    ): SnmpPrinterIdentification? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            try { network?.bindSocket(socket) } catch (_: Exception) {}
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(SNMP_GET_SYSDESCR, SNMP_GET_SYSDESCR.size, address, 161)
            socket.send(packet)

            val receiveData = ByteArray(1500)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)

            val text = String(receiveData, 0, receivePacket.length)
            return@withContext parseBrandAndModel(text)
        } catch (e: Exception) {
            // SNMP query timed out or port closed
            return@withContext null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun parseBrandAndModel(rawSnmpResponse: String): SnmpPrinterIdentification {
        val lower = rawSnmpResponse.lowercase()
        val brand = when {
            lower.contains("canon") -> PrinterBrand.CANON
            lower.contains("epson") -> PrinterBrand.EPSON
            lower.contains("brother") -> PrinterBrand.BROTHER
            Regex("\\bhp\\b").containsMatchIn(lower) || lower.contains("hewlett") -> PrinterBrand.HP
            lower.contains("samsung") -> PrinterBrand.SAMSUNG
            lower.contains("pantum") -> PrinterBrand.PANTUM
            lower.contains("ricoh") -> PrinterBrand.RICOH
            lower.contains("xerox") -> PrinterBrand.XEROX
            else -> PrinterBrand.GENERIC
        }

        // Clean printable model name
        val printable = rawSnmpResponse.filter { it in ' '..'~' }.trim()
        val model = if (printable.length > 5) printable.take(40) else "${brand.displayName} Printer"
        return SnmpPrinterIdentification(brand = brand, modelName = model)
    }

    /** Supply levels come only from the printer itself; unknown stays empty (no invented numbers). */
    @Suppress("UNUSED_PARAMETER")
    fun defaultBrandTanks(brand: PrinterBrand): List<InkCartridge> = emptyList()
}
