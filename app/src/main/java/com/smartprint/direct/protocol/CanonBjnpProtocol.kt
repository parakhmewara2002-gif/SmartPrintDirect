package com.smartprint.direct.protocol

import com.smartprint.direct.network.DualNetworkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Canon BJNP is NOT implemented. The previous version wrote an invalid 16-byte header followed by
 * the raw document to port 8611 and then reported success, so nothing printed while the app said
 * "printed successfully" (and the fallback to RAW 9100 never ran). It now fails fast so the engine
 * falls back to a protocol that actually works.
 */
object CanonBjnpProtocol {
    @Suppress("UNUSED_PARAMETER")
    suspend fun printViaBjnp(
        ipAddress: String,
        documentStream: InputStream,
        router: DualNetworkRouter
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Canon BJNP not supported"))
    }
}
