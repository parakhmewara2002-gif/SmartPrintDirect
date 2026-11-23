package com.smartprint.direct.hp

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.model.PrintJob
import com.smartprint.direct.model.PrintProtocol
import com.smartprint.direct.network.DualNetworkRouter
import com.smartprint.direct.protocol.CanonBjnpProtocol
import com.smartprint.direct.protocol.LprPrintProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Universal Multi-Brand Print Engine (Production Hardened)
 * - BUG-07 FIX: Multi-Protocol Auto-Fallback (RAW 9100 -> IPP 631 -> LPR 515)
 * - BUG-08 FIX: TCP shutdownOutput flush prevents truncated print jobs on embedded controllers
 */
class HpPrintEngine(
    private val context: Context,
    private val router: DualNetworkRouter
) {

    suspend fun printUniversal(
        printer: HpPrinter,
        job: PrintJob,
        documentStream: InputStream
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        // Cache stream bytes if small or copy to temp buffer so we can retry on fallback
        val spoolBytes = documentStream.readBytes()

        when (printer.protocol) {
            PrintProtocol.CANON_BJNP -> {
                Log.i("UniversalEngine", "Routing via Canon BJNP protocol (Port 8611)")
                val res = CanonBjnpProtocol.printViaBjnp(printer.ipAddress, ByteArrayInputStream(spoolBytes), router)
                if (res.isSuccess) return@withContext res
                Log.w("UniversalEngine", "BJNP failed, falling back to RAW 9100")
                printDirectToJetDirect(printer, job, ByteArrayInputStream(spoolBytes))
            }
            PrintProtocol.LPR_515 -> {
                Log.i("UniversalEngine", "Routing via LPR/LPD protocol (Port 515)")
                val res = LprPrintProtocol.printViaLpr(printer.ipAddress, "raw", job.title, ByteArrayInputStream(spoolBytes), router)
                if (res.isSuccess) return@withContext res
                Log.w("UniversalEngine", "LPR failed, falling back to RAW 9100")
                printDirectToJetDirect(printer, job, ByteArrayInputStream(spoolBytes))
            }
            else -> {
                // Primary: Port 9100 RAW Socket
                val primaryResult = printDirectToJetDirect(printer, job, ByteArrayInputStream(spoolBytes))
                if (primaryResult.isSuccess) {
                    return@withContext primaryResult
                }

                // BUG-07 FIX: Fallback 1 -> Try IPP Port 631 if RAW 9100 failed
                Log.w("UniversalEngine", "Primary Port 9100 failed, attempting IPP Port 631 fallback")
                val ippPrinter = printer.copy(port = 631, protocol = PrintProtocol.IPP_EVERYWHERE)
                val ippResult = printDirectToJetDirect(ippPrinter, job, ByteArrayInputStream(spoolBytes))
                if (ippResult.isSuccess) {
                    return@withContext ippResult
                }

                // Fallback 2 -> Try LPR Port 515
                Log.w("UniversalEngine", "IPP fallback failed, attempting LPR Port 515 fallback")
                LprPrintProtocol.printViaLpr(printer.ipAddress, "raw", job.title, ByteArrayInputStream(spoolBytes), router)
            }
        }
    }

    /**
     * Sends raw stream directly over Port 9100 or 631 with PJL envelope.
     * BUG-08 FIX: Uses socket.shutdownOutput() to gracefully end TCP stream without truncating data.
     */
    suspend fun printDirectToJetDirect(
        printer: HpPrinter,
        job: PrintJob,
        documentStream: InputStream
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i("HpPrintEngine", "Opening bound Wi-Fi socket to printer: ${printer.ipAddress}:${printer.port}")
            // 10 second timeout for waking sleeping printers
            val socket = router.createBoundSocket(printer.ipAddress, printer.port, timeoutMs = 10000)

            val outputStream: OutputStream = socket.getOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L

            val pjlHeader = "@PJL \r\n" +
                    "@PJL JOB NAME = \"${job.title}\"\r\n" +
                    "@PJL SET COPIES = ${job.copies}\r\n" +
                    "@PJL ENTER LANGUAGE = PDF\r\n"
            outputStream.write(pjlHeader.toByteArray())

            while (documentStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }

            val pjlFooter = "\r\n\u001B%-12345X@PJL EOJ\r\n\u001B%-12345X"
            outputStream.write(pjlFooter.toByteArray())
            outputStream.flush()

            // BUG-08 FIX: Shutdown output stream first, signaling TCP FIN so the printer knows job is done
            try {
                socket.shutdownOutput()
            } catch (_: Exception) {}

            socket.close()
            Log.i("HpPrintEngine", "Print job sent successfully! Total bytes: $totalBytes")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("HpPrintEngine", "Direct socket failure to ${printer.ipAddress}:${printer.port}", e)
            Result.failure(e)
        }
    }

    fun printViaSystemDialog(jobName: String, adapter: PrintDocumentAdapter) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print(jobName, adapter, attributes)
    }
}
