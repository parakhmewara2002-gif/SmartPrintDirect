package com.smartprint.direct.protocol

import android.content.Context
import android.util.Log
import com.smartprint.direct.network.DualNetworkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Line Printer Remote (LPR/LPD) RFC 1179 implementation (Port 515)
 * - BUG-06 FIX: Eliminates OutOfMemoryError by streaming via disk spool instead of heap byte arrays.
 */
object LprPrintProtocol {

    private const val LPR_PORT = 515

    suspend fun printViaLpr(
        ipAddress: String,
        queueName: String = "raw",
        jobName: String = "SmartPrintJob",
        documentStream: InputStream,
        router: DualNetworkRouter
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var tempSpoolFile: File? = null
        try {
            Log.i("LprPrint", "Connecting to LPD printer at $ipAddress:$LPR_PORT queue=$queueName")

            // BUG-06 FIX: Stream to spool file to calculate size without heap OOM
            tempSpoolFile = File.createTempFile("lpr_spool_", ".tmp")
            FileOutputStream(tempSpoolFile).use { fos ->
                documentStream.copyTo(fos, bufferSize = 8192)
            }
            val docSize = tempSpoolFile.length()

            val socket: Socket = router.createBoundSocket(ipAddress, LPR_PORT, timeoutMs = 8000)
            val out: OutputStream = socket.getOutputStream()
            val input: InputStream = socket.getInputStream()

            // 1. Send Receive a printer job command: \x02 + queue + \n
            out.write("\u0002$queueName\n".toByteArray())
            out.flush()
            checkAck(input)

            // 2. Send Control file: \x02 + size + " cfA001localhost\n"
            val controlContent = "Hlocalhost\nPmobile\nJ$jobName\nldfA001localhost\nUdfA001localhost\nN$jobName\n"
            val cfBytes = controlContent.toByteArray()
            out.write("\u0002${cfBytes.size} cfA001localhost\n".toByteArray())
            out.flush()
            checkAck(input)

            out.write(cfBytes)
            out.write(0) // 0-byte terminator
            out.flush()
            checkAck(input)

            // 3. Send Data file: \x03 + size + " dfA001localhost\n"
            out.write("\u0003$docSize dfA001localhost\n".toByteArray())
            out.flush()
            checkAck(input)

            // Stream from spool file in 8KB chunks
            FileInputStream(tempSpoolFile).use { fis ->
                val buf = ByteArray(8192)
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                }
            }
            out.write(0) // 0-byte terminator
            out.flush()
            checkAck(input)

            socket.shutdownOutput()
            socket.close()
            Log.i("LprPrint", "LPR job successfully completed ($docSize bytes)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("LprPrint", "LPR printing error", e)
            Result.failure(e)
        } finally {
            tempSpoolFile?.delete()
        }
    }

    private fun checkAck(input: InputStream) {
        val ack = input.read()
        if (ack != 0) {
            Log.w("LprPrint", "LPD warning: non-zero ack received: $ack")
        }
    }
}
