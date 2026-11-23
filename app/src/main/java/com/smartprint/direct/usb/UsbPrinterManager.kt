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
import java.io.InputStream

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
                        name.contains("hp", ignoreCase = true) -> PrinterBrand.HP
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

    suspend fun printViaUsb(device: UsbDevice, documentStream: InputStream): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // BUG-05 FIX: Check and request USB Permission if not already granted
            if (!usbManager.hasPermission(device)) {
                val granted = requestUsbPermission(device)
                if (!granted) {
                    return@withContext Result.failure(Exception("USB OTG permission was denied by user"))
                }
            }

            var printerInterface: UsbInterface? = null
            var outEndpoint: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == USB_CLASS_PRINTER) {
                    printerInterface = intf
                    for (e in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(e)
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            outEndpoint = ep
                            break
                        }
                    }
                    break
                }
            }

            if (printerInterface == null || outEndpoint == null) {
                return@withContext Result.failure(Exception("No USB printer OUT endpoint found"))
            }

            val connection = usbManager.openDevice(device) ?: return@withContext Result.failure(Exception("Cannot open USB device"))
            connection.claimInterface(printerInterface, true)

            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (documentStream.read(buffer).also { bytesRead = it } != -1) {
                connection.bulkTransfer(outEndpoint, buffer, bytesRead, 5000)
            }

            connection.releaseInterface(printerInterface)
            connection.close()
            Result.success(true)
        } catch (e: Exception) {
            Log.e("UsbPrinter", "USB Print failed", e)
            Result.failure(e)
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

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        usbManager.requestPermission(device, pendingIntent)
        return deferred.await()
    }
}
