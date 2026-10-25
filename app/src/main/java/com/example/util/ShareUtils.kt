package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

object ShareUtils {

    /**
     * Cleans phone number to international format suitable for WhatsApp.
     * Handles Yemen (default +967) and Saudi (+966) phone numbers.
     */
    fun cleanPhoneNumber(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isBlank()) return ""

        return when {
            digits.startsWith("00") -> digits.removePrefix("00")
            digits.startsWith("+") -> digits.removePrefix("+")
            digits.startsWith("967") || digits.startsWith("966") -> digits
            digits.startsWith("7") && digits.length == 9 -> "967$digits"
            digits.startsWith("07") && digits.length == 10 -> "967${digits.removePrefix("0")}"
            digits.startsWith("05") && digits.length == 10 -> "966${digits.removePrefix("0")}"
            digits.startsWith("5") && digits.length == 9 -> "966$digits"
            else -> digits
        }
    }

    /**
     * Saves a Bitmap to internal cache and returns its content Uri via FileProvider.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "shared_cache").apply { mkdirs() }
        val safeFileName = fileName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val imageFile = File(cacheDir, "${safeFileName}_${System.currentTimeMillis()}.png")

        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    /**
     * Shares an image bitmap to WhatsApp or system chooser with optional phone and caption.
     */
    fun shareImage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        phone: String = "",
        caption: String = ""
    ) {
        try {
            val uri = saveBitmapToCache(context, bitmap, fileName)
            val cleanedPhone = cleanPhoneNumber(phone)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (cleanedPhone.isNotBlank()) {
                    putExtra("jid", "$cleanedPhone@s.whatsapp.net")
                    setPackage("com.whatsapp")
                }
            }

            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // If direct WhatsApp fails (e.g. not installed or jid rejected), fallback to chooser
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (caption.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "مشاركة الفاتورة / كشف الحساب"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة الصورة: ${e.localizedMessage ?: "خطأ غير متوقع"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares formatted text directly to WhatsApp contact or opens system chooser.
     */
    fun shareTextToWhatsApp(context: Context, phone: String, message: String) {
        val cleanedPhone = cleanPhoneNumber(phone)
        if (cleanedPhone.isNotBlank()) {
            try {
                val encoded = URLEncoder.encode(message, "UTF-8")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanedPhone&text=$encoded")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // Fallback to general text share
        shareGeneralText(context, message)
    }

    /**
     * Shares plain text via standard Android chooser.
     */
    fun shareGeneralText(context: Context, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة النص"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر المشاركة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares a generated PDF file via Android chooser.
     */
    fun sharePdf(context: Context, pdfUri: Uri, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة ملف PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
