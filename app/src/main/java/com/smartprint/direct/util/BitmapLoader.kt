package com.smartprint.direct.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Memory-safe, EXIF-aware bitmap decoding.
 * - Subsamples so the long edge stays near [maxEdge] (12MP+ photos otherwise freeze/OOM the app)
 * - Applies EXIF orientation (gallery/camera photos otherwise print sideways)
 */
object BitmapLoader {

    fun decode(context: Context, uri: Uri, maxEdge: Int = 2048): Bitmap? =
        decodeInternal(maxEdge) { context.contentResolver.openInputStream(uri) }

    fun decode(file: File, maxEdge: Int = 2048): Bitmap? =
        decodeInternal(maxEdge) { FileInputStream(file) }

    private fun decodeInternal(maxEdge: Int, open: () -> InputStream?): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (longEdge / (sample * 2) >= maxEdge) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val orientation = try {
                open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            applyOrientation(raw, orientation)
        } catch (_: Throwable) { // includes OutOfMemoryError
            null
        }
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return try {
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (out !== src) src.recycle()
            out
        } catch (_: Throwable) {
            src
        }
    }
}
