package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfAndPrintUtils {

    /**
     * Converts a Bitmap into a standard A4 / Receipt PDF file and returns its URI for sharing or viewing.
     */
    fun createPdfFromBitmap(context: Context, bitmap: Bitmap, fileName: String): Uri {
        // Standard A4 width: 595 points, height: 842 points (at 72 dpi)
        val pageWidth = 595
        val scale = pageWidth.toFloat() / bitmap.width.toFloat()
        val pageHeight = (bitmap.height.toFloat() * scale).toInt().coerceAtLeast(842)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        val destRect = Rect(0, 0, pageWidth, (bitmap.height * scale).toInt())
        canvas.drawBitmap(bitmap, null, destRect, null)

        pdfDocument.finishPage(page)

        val cacheDir = File(context.cacheDir, "shared_cache").apply { mkdirs() }
        val pdfFile = File(cacheDir, "$fileName.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
    }

    /**
     * Launches the native Android print dialog, allowing the user to print to any WiFi/Network printer
     * or "Save as PDF" directly to device storage.
     */
    fun printViaSystemManager(context: Context, bitmap: Bitmap, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "خدمة الطباعة غير متاحة في هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val scale = pageWidth.toFloat() / bitmap.width.toFloat()
                val pageHeight = (bitmap.height.toFloat() * scale).toInt().coerceAtLeast(842)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                val page = pdfDocument.startPage(pageInfo)

                val canvas = page.canvas
                val destRect = Rect(0, 0, pageWidth, (bitmap.height * scale).toInt())
                canvas.drawBitmap(bitmap, null, destRect, null)
                pdfDocument.finishPage(page)

                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage)
                } finally {
                    pdfDocument.close()
                }
            }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }
}
