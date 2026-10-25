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
     * Cleans phone number and formats it with proper country code (Yemen +967 or Saudi +966).
     */
    fun cleanPhoneNumber(rawPhone: String): String {
        val digits = rawPhone.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return ""

        return when {
            digits.startsWith("967") -> digits
            digits.startsWith("966") -> digits
            digits.startsWith("00967") -> digits.removePrefix("00")
            digits.startsWith("00966") -> digits.removePrefix("00")
            // Saudi mobile starting with 05
            digits.startsWith("05") && digits.length == 10 -> "966" + digits.substring(1)
            digits.startsWith("5") && digits.length == 9 -> "966$digits"
            // Yemeni mobile starting with 7 (77, 73, 71, 70, 78) with 9 digits
            digits.startsWith("7") && digits.length == 9 -> "967$digits"
            digits.startsWith("07") && digits.length == 10 -> "967" + digits.substring(1)
            else -> digits
        }
    }

    /**
     * Shares message directly to customer via WhatsApp with one touch.
     */
    fun shareTextToWhatsApp(context: Context, phone: String, message: String) {
        val cleanedPhone = cleanPhoneNumber(phone)
        try {
            if (cleanedPhone.isNotEmpty()) {
                val encodedText = URLEncoder.encode(message, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$cleanedPhone&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
            } else {
                // Open WhatsApp chooser directly
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            // Fallback to standard share sheet or browser
            try {
                if (cleanedPhone.isNotEmpty()) {
                    val encodedText = URLEncoder.encode(message, "UTF-8")
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanedPhone&text=$encodedText"))
                    context.startActivity(browserIntent)
                } else {
                    shareGeneralText(context, message)
                }
            } catch (_: Exception) {
                shareGeneralText(context, message)
            }
        }
    }

    /**
     * Shares image (and optional text) to WhatsApp or system apps.
     */
    fun shareImage(context: Context, bitmap: Bitmap, fileName: String, phone: String = "", caption: String = "") {
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
                if (cleanedPhone.isNotEmpty()) {
                    putExtra("jid", "$cleanedPhone@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to general image sharing
            try {
                val uri = saveBitmapToCache(context, bitmap, fileName)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (caption.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة كصورة"))
            } catch (e: Exception) {
                Toast.makeText(context, "فشل حفظ ومشاركة الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Shares a generated PDF file via Android Sharesheet or WhatsApp.
     */
    fun sharePdf(context: Context, pdfUri: Uri, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذرت مشاركة ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareGeneralText(context: Context, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة عبر"))
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "shared_cache").apply { mkdirs() }
        val file = File(cacheDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
