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
import com.smartprint.direct.usb.UsbPrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Print engine. Every attempt is a REAL transport (RAW 9100, LPR 515, USB). A transport that cannot
 * deliver the job returns a failure, so the next one is tried and the user sees a truthful result.
 *
 * Fixed vs. the previous version:
 *  - the document was read fully into the heap (OOM on big PDFs) -> now spooled to a cache file
 *  - the "IPP fallback" wrote raw PJL+PDF bytes to port 631 (not HTTP) and always reported success,
 *    which also blocked the LPR fallback -> removed
 *  - sockets leaked on any exception -> always closed
 *  - PJL job name was not escaped (quotes / newlines broke or injected into the PJL stream)
 *  - the PJL header lacked the leading UEL, and the job could be cut off by closing right after FIN
 *  - USB_OTG printers were selectable but no code path ever printed to them
 */
class HpPrintEngine(
    private val context: Context,
    private val router: DualNetworkRouter,
    private val usb: UsbPrinterManager? = null
) {

    private data class Attempt(val label: String, val run: suspend (File) -> Result<Boolean>)

    suspend fun printUniversal(
        printer: HpPrinter,
        job: PrintJob,
        documentStream: InputStream
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var spool: File? = null
        try {
            spool = File.createTempFile("print_spool_", ".bin", context.cacheDir)
            documentStream.use { input ->
                FileOutputStream(spool).use { out -> input.copyTo(out, 16 * 1024) }
            }
            if (spool.length() == 0L) {
                return@withContext Result.failure(Exception("Document is empty"))
            }

            val raw = Attempt("RAW 9100") { f ->
                val port = if (printer.protocol == PrintProtocol.RAW_9100 && printer.port > 0) printer.port else 9100
                sendRaw(printer.ipAddress, port, job, f)
            }
            val lpr = Attempt("LPR 515") { f ->
                LprPrintProtocol.printViaLpr(printer.ipAddress, "raw", job.title, FileInputStream(f), router)
            }
            val bjnp = Attempt("Canon BJNP") { f ->
                CanonBjnpProtocol.printViaBjnp(printer.ipAddress, FileInputStream(f), router)
            }
            val usbAttempt = Attempt("USB") { f ->
                usb?.printViaUsbById(printer.id, f)
                    ?: Result.failure(Exception("USB printing is not available"))
            }

            val order = when (printer.protocol) {
                PrintProtocol.USB_OTG -> listOf(usbAttempt)
                PrintProtocol.CANON_BJNP -> listOf(bjnp, raw, lpr)
                PrintProtocol.LPR_515 -> listOf(lpr, raw)
                else -> listOf(raw, lpr) // RAW_9100 and IPP-advertised printers (IPP itself is not implemented here)
            }

            val errors = mutableListOf<String>()
            for (attempt in order) {
                val res = attempt.run(spool)
                if (res.isSuccess) return@withContext res
                val msg = res.exceptionOrNull()?.message ?: "failed"
                Log.w("PrintEngine", "${attempt.label} failed: $msg")
                if (attempt.label != "Canon BJNP") errors += "${attempt.label}: $msg"
            }
            Result.failure(Exception(errors.joinToString(" | ").ifBlank { "No transport available" }))
        } catch (e: Throwable) { // includes OutOfMemoryError / disk full
            Log.e("PrintEngine", "Print failed", e)
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        } finally {
            spool?.delete()
        }
    }

    private fun sendRaw(host: String, port: Int, job: PrintJob, file: File): Result<Boolean> {
        var socket: java.net.Socket? = null
        return try {
            Log.i("PrintEngine", "Opening Wi-Fi-bound socket to $host:$port")
            socket = router.createBoundSocket(host, port, timeoutMs = 10000)
            socket.soTimeout = 15000
            val out = socket.getOutputStream()

            val name = job.title.filter { it in ' '..'~' && it != '"' }.take(60).ifBlank { "SmartPrintJob" }
            val copies = job.copies.coerceIn(1, 99)
            val uel = "\u001B%-12345X"
            val header = uel + "@PJL\r\n" +
                "@PJL JOB NAME = \"$name\"\r\n" +
                "@PJL SET COPIES = $copies\r\n" +
                "@PJL ENTER LANGUAGE = PDF\r\n"
            out.write(header.toByteArray(Charsets.US_ASCII))

            var total = 0L
            FileInputStream(file).use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
            }
            out.write(("\r\n" + uel + "@PJL EOJ\r\n" + uel).toByteArray(Charsets.US_ASCII))
            out.flush()

            // Half-close, then wait for the printer to close its side so the tail of the job is not
            // lost to a TCP reset when we drop the connection immediately.
            try { socket.shutdownOutput() } catch (_: Exception) {}
            try {
                socket.soTimeout = 3000
                val sink = ByteArray(256)
                @Suppress("ControlFlowWithEmptyBody")
                while (socket.getInputStream().read(sink) >= 0) { }
            } catch (_: Exception) { /* timeout or reset after full delivery is fine */ }

            Log.i("PrintEngine", "Job delivered ($total bytes)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("PrintEngine", "RAW socket failure $host:$port", e)
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /** Raw PJL command (reports etc.). Returns the real delivery result. */
    suspend fun sendPjl(printer: HpPrinter, commands: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var socket: java.net.Socket? = null
        try {
            socket = router.createBoundSocket(printer.ipAddress, 9100, timeoutMs = 8000)
            val uel = "\u001B%-12345X"
            socket.getOutputStream().apply {
                write((uel + "@PJL\r\n" + commands + "\r\n" + uel).toByteArray(Charsets.US_ASCII))
                flush()
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
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
