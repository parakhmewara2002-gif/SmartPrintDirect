package com.smartprint.direct.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object IdCardPdfGenerator {

    /**
     * Generates a standard A4 PDF (595 x 842 pt) placing Front ID Card on top half
     * and Back ID Card on bottom half, exactly like a 2-in-1 ID Photocopy machine.
     */
    fun createIdCardA4Pdf(
        context: Context,
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        isColor: Boolean
    ): Uri {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paint white background
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        // Card styling
        val targetWidth = 360f // standard ID card width in points
        val targetHeight = 226f // standard 85.6 x 53.98 mm ratio in points

        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // ColorMatrix for B&W Xerox Photocopy mode
        val bitmapPaint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            if (!isColor) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        }

        val centerX = (595f - targetWidth) / 2f

        // 1. Draw Front ID Card (Top Half: Y = 110f)
        val frontY = 110f
        val frontRect = RectF(centerX, frontY, centerX + targetWidth, frontY + targetHeight)
        canvas.drawBitmap(frontBitmap, null, fitRect(frontBitmap, frontRect), bitmapPaint)
        canvas.drawRoundRect(frontRect, 10f, 10f, borderPaint)

        // 2. Draw Back ID Card (Bottom Half: Y = 460f)
        val backY = 460f
        val backRect = RectF(centerX, backY, centerX + targetWidth, backY + targetHeight)
        canvas.drawBitmap(backBitmap, null, fitRect(backBitmap, backRect), bitmapPaint)
        canvas.drawRoundRect(backRect, 10f, 10f, borderPaint)

        // Subtle Header Text
        val textPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("ID CARD 2-IN-1 COPY · FRONT", centerX, frontY - 12f, textPaint)
        canvas.drawText("ID CARD 2-IN-1 COPY · BACK", centerX, backY - 12f, textPaint)

        pdfDocument.finishPage(page)

        val outputFile = File(context.cacheDir, "id_card_copy_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
    }

    /** Largest centred rect inside [frame] with the bitmap's own aspect ratio (no stretching). */
    private fun fitRect(bmp: Bitmap, frame: RectF): RectF {
        val scale = minOf(frame.width() / bmp.width, frame.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val l = frame.left + (frame.width() - w) / 2f
        val t = frame.top + (frame.height() - h) / 2f
        return RectF(l, t, l + w, t + h)
    }

    fun uriToBitmap(context: Context, uri: Uri): Bitmap? = BitmapLoader.decode(context, uri, 2048)

    fun createPlaceholderCard(text: String, colorHex: Long): Bitmap {
        val w = 720
        val h = 450
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply { color = Color.rgb(240, 245, 250) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        val border = Paint().apply {
            color = Color.rgb(0, 150, 214)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        c.drawRoundRect(10f, 10f, (w - 10).toFloat(), (h - 10).toFloat(), 20f, 20f, border)

        val tp = Paint().apply {
            color = Color.BLACK
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        c.drawText(text, (w / 2).toFloat(), (h / 2).toFloat(), tp)
        return bmp
    }
}
