package com.smartprint.direct.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.smartprint.direct.model.PaperSize
import java.io.File
import java.io.FileOutputStream

/** Builds print-ready PDFs from images (printers expect PDF, not raw JPEG/PNG bytes). */
object PdfBuilder {

    /** Page size in PostScript points (1/72 in). */
    private fun pageSize(paper: PaperSize): Pair<Int, Int> = when (paper) {
        PaperSize.A4 -> 595 to 842
        PaperSize.LETTER -> 612 to 792
        PaperSize.LEGAL -> 612 to 1008
        PaperSize.PHOTO_4X6 -> 288 to 432
        PaperSize.PHOTO_5X7 -> 360 to 504
    }

    /** Single image -> single-page PDF. [fill] crops to cover the whole page (borderless). */
    fun imageToPdf(context: Context, imageUri: Uri, paper: PaperSize, fill: Boolean, out: File): File? {
        val bmp = BitmapLoader.decode(context, imageUri, 3000) ?: return null
        return try {
            writePdf(listOf(bmp), pageSize(paper), fill, out)
            out
        } finally {
            bmp.recycle()
        }
    }

    /** Scanned JPEG pages -> multi-page A4 PDF. */
    fun scansToPdf(pages: List<File>, out: File): File? {
        if (pages.isEmpty()) return null
        val doc = PdfDocument()
        try {
            val (pw, ph) = pageSize(PaperSize.A4)
            var number = 0
            for (f in pages) {
                val bmp = BitmapLoader.decode(f, 2400) ?: continue
                number++
                drawPage(doc, number, pw, ph, bmp, fill = false)
                bmp.recycle()
            }
            if (number == 0) return null
            FileOutputStream(out).use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    private fun writePdf(bitmaps: List<Bitmap>, size: Pair<Int, Int>, fill: Boolean, out: File) {
        val doc = PdfDocument()
        try {
            bitmaps.forEachIndexed { i, b -> drawPage(doc, i + 1, size.first, size.second, b, fill) }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun drawPage(doc: PdfDocument, number: Int, pw: Int, ph: Int, bmp: Bitmap, fill: Boolean) {
        val info = PdfDocument.PageInfo.Builder(pw, ph, number).create()
        val page = doc.startPage(info)
        val canvas: Canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Auto-rotate landscape images onto portrait pages so they use the paper fully
        val rotate = bmp.width > bmp.height && ph > pw
        val srcW = if (rotate) bmp.height else bmp.width
        val srcH = if (rotate) bmp.width else bmp.height
        val scale = if (fill) maxOf(pw / srcW.toFloat(), ph / srcH.toFloat())
        else minOf(pw / srcW.toFloat(), ph / srcH.toFloat())
        val dw = srcW * scale
        val dh = srcH * scale

        canvas.save()
        canvas.clipRect(0f, 0f, pw.toFloat(), ph.toFloat())
        canvas.translate((pw - dw) / 2f, (ph - dh) / 2f)
        if (rotate) {
            canvas.translate(dw, 0f)
            canvas.rotate(90f)
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, dh, dw), paint)
        } else {
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, dw, dh), paint)
        }
        canvas.restore()
        doc.finishPage(page)
    }
}
