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

    suspend fun identifyPrinter(ipAddress: String, timeoutMs: Int = 2000): SnmpPrinterIdentification? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(SNMP_GET_SYSDESCR, SNMP_GET_SYSDESCR.size, address, 161)
            socket.send(packet)

            val receiveData = ByteArray(1500)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)
            socket.close()

            val text = String(receiveData, 0, receivePacket.length)
            return@withContext parseBrandAndModel(text)
        } catch (e: Exception) {
            // SNMP query timed out or port closed
            return@withContext null
        }
    }

    fun parseBrandAndModel(rawSnmpResponse: String): SnmpPrinterIdentification {
        val lower = rawSnmpResponse.lowercase()
        val brand = when {
            lower.contains("canon") -> PrinterBrand.CANON
            lower.contains("epson") -> PrinterBrand.EPSON
            lower.contains("brother") -> PrinterBrand.BROTHER
            lower.contains("hp") || lower.contains("hewlett-packard") -> PrinterBrand.HP
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

    fun defaultBrandTanks(brand: PrinterBrand): List<InkCartridge> {
        return when (brand) {
            PrinterBrand.CANON -> listOf(
                InkCartridge("Black (PGBK)", 0xFF1A1A1AL, 80),
                InkCartridge("Cyan", 0xFF00AEEFL, 75),
                InkCartridge("Magenta", 0xFFEC008CL, 70),
                InkCartridge("Yellow", 0xFFFFF100L, 85)
            )
            PrinterBrand.EPSON -> listOf(
                InkCartridge("Black", 0xFF1A1A1AL, 90),
                InkCartridge("Cyan", 0xFF00AEEFL, 80),
                InkCartridge("Magenta", 0xFFEC008CL, 75),
                InkCartridge("Yellow", 0xFFFFF100L, 80)
            )
            PrinterBrand.BROTHER -> listOf(
                InkCartridge("Black (BTD60)", 0xFF1A1A1AL, 85),
                InkCartridge("Cyan (BT5000C)", 0xFF00AEEFL, 70),
                InkCartridge("Magenta (BT5000M)", 0xFFEC008CL, 65),
                InkCartridge("Yellow (BT5000Y)", 0xFFFFF100L, 85)
            )
            else -> listOf(
                InkCartridge("Black", 0xFF1A1A1AL, 85),
                InkCartridge("Cyan", 0xFF00AEEFL, 70),
                InkCartridge("Magenta", 0xFFEC008CL, 65),
                InkCartridge("Yellow", 0xFFFFF100L, 90)
            )
        }
    }
}
