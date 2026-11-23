package com.smartprint.direct.protocol

import android.util.Log
import com.smartprint.direct.network.DualNetworkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Canon BJNP (Bubble Jet Network Protocol) implementation (Port 8611)
 * Used by Canon PIXMA, MAXIFY, and imageCLASS network printers.
 */
object CanonBjnpProtocol {

    private const val BJNP_PORT = 8611
    private val BJNP_MAGIC = byteArrayOf('B'.code.toByte(), 'J'.code.toByte(), 'N'.code.toByte(), 'P'.code.toByte())

    suspend fun printViaBjnp(
        ipAddress: String,
        documentStream: InputStream,
        router: DualNetworkRouter
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i("CanonBjnp", "Connecting to Canon printer at $ipAddress:$BJNP_PORT via BJNP")
            val socket: Socket = router.createBoundSocket(ipAddress, BJNP_PORT, timeoutMs = 6000)
            val out: OutputStream = socket.getOutputStream()

            // BJNP Command 0x11: Start Print Job
            val header = ByteArray(16)
            System.arraycopy(BJNP_MAGIC, 0, header, 0, 4)
            header[4] = 0x01 // Command: Print
            header[5] = 0x11
            header[8] = 0x00 // Sequence
            out.write(header)
            out.flush()

            // Stream document payload
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var total = 0L
            while (documentStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                total += bytesRead
            }
            out.flush()
            socket.close()
            Log.i("CanonBjnp", "Canon BJNP print job finished ($total bytes)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("CanonBjnp", "BJNP printing failed", e)
            Result.failure(e)
        }
    }
}
