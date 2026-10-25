package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
     * Checks if a package (like WhatsApp or WhatsApp Business) is installed.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds installed WhatsApp package (regular WhatsApp or WhatsApp Business).
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            if (isPackageInstalled(context, pkg)) {
                return pkg
            }
        }
        return null
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
     * Enhanced WhatsApp image and invoice/statement sharing.
     * Directly targets WhatsApp / WhatsApp Business with the recipient phone number and image attachment.
     */
    fun shareImageToWhatsApp(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        phone: String = "",
        caption: String = ""
    ) {
        try {
            val uri = saveBitmapToCache(context, bitmap, fileName)
            val cleanedPhone = cleanPhoneNumber(phone)
            val waPackage = getInstalledWhatsAppPackage(context)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (cleanedPhone.isNotBlank()) {
                    putExtra("jid", "$cleanedPhone@s.whatsapp.net")
                }
                if (waPackage != null) {
                    setPackage(waPackage)
                }
            }

            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // If package-specific send failed, try open via chooser targeting WhatsApp or system
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (caption.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "إرسال عبر واتساب"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة الصورة: ${e.localizedMessage ?: "خطأ"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Backwards-compatible alias for shareImage (routes directly to improved WhatsApp sharing).
     */
    fun shareImage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        phone: String = "",
        caption: String = ""
    ) {
        shareImageToWhatsApp(context, bitmap, fileName, phone, caption)
    }

    /**
     * Improved WhatsApp direct messaging.
     * Tries whatsapp:// scheme first, falls back to web API, with user-friendly Arabic error if WhatsApp is absent.
     */
    fun shareTextToWhatsApp(context: Context, phone: String, message: String) {
        val cleanedPhone = cleanPhoneNumber(phone)
        val encoded = try { URLEncoder.encode(message, "UTF-8") } catch (_: Exception) { message }

        if (cleanedPhone.isNotBlank()) {
            try {
                // Try deep link scheme directly into WhatsApp app
                val uriScheme = Uri.parse("whatsapp://send?phone=$cleanedPhone&text=$encoded")
                val intent = Intent(Intent.ACTION_VIEW, uriScheme)
                val waPackage = getInstalledWhatsAppPackage(context)
                if (waPackage != null) {
                    intent.setPackage(waPackage)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback to web link which opens WhatsApp app or prompts install
                try {
                    val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanedPhone&text=$encoded")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                    context.startActivity(webIntent)
                    return
                } catch (_: Exception) {}
            }
        } else {
            // No phone number provided - open WhatsApp contact selector
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    val waPackage = getInstalledWhatsAppPackage(context)
                    if (waPackage != null) {
                        setPackage(waPackage)
                    }
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        Toast.makeText(context, "تطبيق واتساب غير مثبت على الجهاز", Toast.LENGTH_SHORT).show()
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
