package com.smartprint.direct.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import com.smartprint.direct.model.ConnectionType
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.model.PrinterBrand
import com.smartprint.direct.model.PrintProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream

/**
 * Universal USB OTG Printer Manager (Production Hardened)
 * - BUG-05 FIX: Handles Android UsbManager runtime permissions explicitly
 */
class UsbPrinterManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        const val USB_CLASS_PRINTER = 7
        const val ACTION_USB_PERMISSION = "com.smartprint.direct.USB_PERMISSION"
    }

    fun findConnectedUsbPrinters(): List<HpPrinter> {
        val list = mutableListOf<HpPrinter>()
        val deviceList = usbManager.deviceList
        for ((_, device) in deviceList) {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == USB_CLASS_PRINTER) {
                    val name = device.productName ?: device.deviceName ?: "USB Printer"
                    val brand = when {
                        name.contains("canon", ignoreCase = true) -> PrinterBrand.CANON
                        name.contains("epson", ignoreCase = true) -> PrinterBrand.EPSON
                        name.contains("brother", ignoreCase = true) -> PrinterBrand.BROTHER
                        Regex("\\bhp\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) || name.contains("hewlett", ignoreCase = true) -> PrinterBrand.HP
                        name.contains("pantum", ignoreCase = true) -> PrinterBrand.PANTUM
                        else -> PrinterBrand.GENERIC
                    }
                    list.add(
                        HpPrinter(
                            id = "usb_${device.deviceId}",
                            name = name,
                            model = "${brand.displayName} USB OTG",
                            brand = brand,
                            protocol = PrintProtocol.USB_OTG,
                            ipAddress = "USB-OTG",
                            connectionType = ConnectionType.USB_OTG,
                            isOnline = true
                        )
                    )
                    break
                }
            }
        }
        return list
    }

    fun findDeviceById(id: String): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { "usb_${it.deviceId}" == id }

    suspend fun printViaUsbById(id: String, file: File): Result<Boolean> {
        val device = findDeviceById(id)
            ?: return Result.failure(Exception("USB printer disconnected - reconnect the OTG cable"))
        return printViaUsb(device, file)
    }

    suspend fun printViaUsb(device: UsbDevice, file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        var connection: UsbDeviceConnection? = null
        var claimed: UsbInterface? = null
        try {
            if (!usbManager.hasPermission(device)) {
                val granted = withTimeoutOrNull(60_000) { requestUsbPermission(device) }
                if (granted != true) {
                    return@withContext Result.failure(Exception("USB permission was denied or timed out"))
                }
            }

            var printerInterface: UsbInterface? = null
            var outEndpoint: UsbEndpoint? = null
            loop@ for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass != USB_CLASS_PRINTER) continue
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        printerInterface = intf
                        outEndpoint = ep
                        break@loop
                    }
                }
            }
            if (printerInterface == null || outEndpoint == null) {
                return@withContext Result.failure(Exception("No USB printer OUT endpoint found"))
            }

            connection = usbManager.openDevice(device)
                ?: return@withContext Result.failure(Exception("Cannot open USB device"))
            if (!connection.claimInterface(printerInterface, true)) {
                return@withContext Result.failure(Exception("Could not claim the USB printer interface"))
            }
            claimed = printerInterface

            val buffer = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    var sent = 0
                    while (sent < n) {
                        // bulkTransfer returns the byte count or a negative value on failure/timeout
                        val r = connection.bulkTransfer(outEndpoint, buffer, sent, n - sent, 10_000)
                        if (r <= 0) throw java.io.IOException("USB transfer failed (code $r)")
                        sent += r
                    }
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("UsbPrinter", "USB Print failed", e)
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        } finally {
            try { claimed?.let { connection?.releaseInterface(it) } } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    private suspend fun requestUsbPermission(device: UsbDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    deferred.complete(granted)
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        return try {
            usbManager.requestPermission(device, pendingIntent)
            deferred.await()
        } finally {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }
}
