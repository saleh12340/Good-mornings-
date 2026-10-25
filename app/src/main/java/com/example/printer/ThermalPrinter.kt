package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.*
import com.example.model.Customer
import com.example.model.TransactionEntry
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executors

object ThermalPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Renders an invoice into a crisp black-and-white Bitmap suitable for thermal printing.
     * Supports 58mm (384px) and 80mm (576px) widths.
     */
    fun createInvoiceBitmap(
        customer: Customer,
        invoice: TransactionEntry,
        is80mm: Boolean = false
    ): Bitmap {
        val width = if (is80mm) 576 else 384
        val padding = 16f

        // Estimated height calculation
        val rowHeight = 28f
        val itemsCount = invoice.items.size.coerceAtLeast(1)
        val estimatedHeight = (380 + itemsCount * rowHeight + 160).toInt()

        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (is80mm) 24f else 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val paintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (is80mm) 20f else 15f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }

        val paintRightBold = Paint(paintNormal).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        val paintLeft = Paint(paintNormal).apply {
            textAlign = Paint.Align.LEFT
        }

        var y = 35f

        // Header
        canvas.drawText("بقالة العزي للمواد الغذائية", width / 2f, y, paintText)
        y += 24f
        val paintSub = Paint(paintText).apply { textSize = if (is80mm) 18f else 14f; typeface = Typeface.DEFAULT }
        canvas.drawText("هاتف: 777123456 - صنعاء", width / 2f, y, paintSub)
        y += 18f

        // Separator line
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 20f

        // Invoice Meta
        val invNum = invoice.invoiceNumber.ifBlank { invoice.title }
        canvas.drawText("فاتورة: $invNum", width - padding, y, paintRightBold)
        canvas.drawText("الدفع: ${invoice.paymentType.label}", padding, y, paintLeft)
        y += 20f

        canvas.drawText("التاريخ: ${invoice.date}", width - padding, y, paintNormal)
        y += 20f

        canvas.drawText("العميل: ${customer.name}", width - padding, y, paintRightBold)
        y += 22f

        // Table Header
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 18f

        val col1 = width - padding           // الصنف (Right)
        val col2 = width * 0.48f             // الكمية
        val col3 = width * 0.32f             // السعر
        val col4 = padding                   // الإجمالي (Left)

        val paintColCenter = Paint(paintNormal).apply { textAlign = Paint.Align.CENTER }

        canvas.drawText("الصنف", col1, y, paintRightBold)
        canvas.drawText("الكمية", col2, y, paintColCenter)
        canvas.drawText("السعر", col3, y, paintColCenter)
        canvas.drawText("الإجمالي", col4, y, paintLeft)
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 20f

        // Items
        if (invoice.items.isEmpty()) {
            canvas.drawText("مبيعات متنوعة", col1, y, paintNormal)
            canvas.drawText("1", col2, y, paintColCenter)
            canvas.drawText("${invoice.amount.toInt()}", col3, y, paintColCenter)
            canvas.drawText("${invoice.amount.toInt()}", col4, y, paintLeft)
            y += rowHeight
        } else {
            for (item in invoice.items) {
                val itemName = if (item.name.length > 14) item.name.take(13) + "…" else item.name
                canvas.drawText(itemName, col1, y, paintNormal)
                canvas.drawText("${item.quantity}", col2, y, paintColCenter)
                canvas.drawText("${item.unitPrice.toInt()}", col3, y, paintColCenter)
                canvas.drawText("${item.total.toInt()}", col4, y, paintLeft)
                y += rowHeight
            }
        }

        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 22f

        // Totals
        val curr = invoice.currency.symbol
        canvas.drawText("الإجمالي العام:", width - padding, y, paintRightBold)
        canvas.drawText("${String.format("%,.0f", invoice.amount)} $curr", padding, y, paintLeft)
        y += 20f

        if (invoice.discount > 0) {
            canvas.drawText("الخصم:", width - padding, y, paintNormal)
            canvas.drawText("${String.format("%,.0f", invoice.discount)} $curr", padding, y, paintLeft)
            y += 20f
        }

        canvas.drawText("المبلغ الواصل:", width - padding, y, paintRightBold)
        canvas.drawText("${String.format("%,.0f", invoice.paid)} $curr", padding, y, paintLeft)
        y += 20f

        canvas.drawText("المبلغ المتبقي:", width - padding, y, paintRightBold)
        canvas.drawText("${String.format("%,.0f", invoice.remaining)} $curr", padding, y, paintLeft)
        y += 26f

        // Signatures
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 22f
        canvas.drawText("توقيع البائع: ..........", width - padding, y, paintNormal)
        canvas.drawText("توقيع العميل: ..........", padding, y, paintLeft)
        y += 30f

        // Footer
        canvas.drawText("شكراً لتسوقكم معنا — بقالة العزي", width / 2f, y, paintSub)
        y += 20f

        // Return trimmed bitmap to actual drawn height
        val finalHeight = y.toInt() + 10
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight.coerceAtMost(bitmap.height))
    }

    /**
     * Renders a full Customer Account Statement into a crisp monochrome Bitmap.
     */
    fun createStatementBitmap(
        customer: Customer,
        is80mm: Boolean = false
    ): Bitmap {
        val width = if (is80mm) 576 else 384
        val padding = 16f
        val entries = customer.entries.sortedBy { it.date }
        val rowHeight = 26f
        val estimatedHeight = (360 + entries.size.coerceAtLeast(1) * rowHeight + 160).toInt()

        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (is80mm) 24f else 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val paintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 13f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }

        val paintRightBold = Paint(paintNormal).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        val paintLeft = Paint(paintNormal).apply {
            textAlign = Paint.Align.LEFT
        }

        val paintLeftBold = Paint(paintRightBold).apply {
            textAlign = Paint.Align.LEFT
        }

        val paintCenter = Paint(paintNormal).apply {
            textAlign = Paint.Align.CENTER
        }

        var y = 35f

        // Header
        canvas.drawText("بقالة العزي للمواد الغذائية", width / 2f, y, paintText)
        y += 24f
        val paintSub = Paint(paintText).apply { textSize = if (is80mm) 18f else 13f; typeface = Typeface.DEFAULT }
        canvas.drawText("هاتف: 777123456 - صنعاء", width / 2f, y, paintSub)
        y += 18f

        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 20f

        canvas.drawText("كشف حساب العميل: ${customer.name}", width - padding, y, paintRightBold)
        y += 20f
        if (customer.phone.isNotBlank()) {
            canvas.drawText("الهاتف: ${customer.phone}", width - padding, y, paintNormal)
            y += 20f
        }
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("تاريخ الكشف: $dateStr", width - padding, y, paintNormal)
        y += 22f

        // Table Header
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 18f

        val col1 = width - padding     // البيان / التاريخ (Right)
        val col2 = width * 0.40f       // نوع (Center)
        val col3 = padding             // المبلغ (Left)

        canvas.drawText("التاريخ والبيان", col1, y, paintRightBold)
        canvas.drawText("النوع", col2, y, paintCenter)
        canvas.drawText("المبلغ", col3, y, paintLeftBold)
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 18f

        // Rows
        var balYER = 0.0
        var balSAR = 0.0

        if (entries.isEmpty()) {
            canvas.drawText("لا توجد حركات مسجلة", width / 2f, y, paintCenter)
            y += 24f
        } else {
            for (entry in entries) {
                val kindLabel = when (entry.kind) {
                    com.example.model.EntryKind.CREDIT -> "له"
                    com.example.model.EntryKind.DEBIT -> "عليه"
                    com.example.model.EntryKind.INVOICE -> "فاتورة"
                }

                if (entry.currency == com.example.model.AppCurrency.SAR) {
                    balSAR += if (entry.kind == com.example.model.EntryKind.CREDIT) -entry.amount else entry.amount
                } else {
                    balYER += if (entry.kind == com.example.model.EntryKind.CREDIT) -entry.amount else entry.amount
                }

                val titleText = if (entry.note.isNotBlank()) "${entry.date.split(" ")[0]} ${entry.title} (${entry.note})" else "${entry.date.split(" ")[0]} ${entry.title}"
                val shortTitle = if (titleText.length > 20) titleText.take(19) + "…" else titleText

                canvas.drawText(shortTitle, col1, y, paintNormal)
                canvas.drawText(kindLabel, col2, y, paintCenter)
                canvas.drawText("${entry.amount.toInt()} ${entry.currency.symbol}", col3, y, paintLeft)
                y += rowHeight
            }
        }

        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 22f

        // Summary
        canvas.drawText("الرصيد المتبقي (يمني):", width - padding, y, paintRightBold)
        canvas.drawText("${String.format("%,.0f", balYER)} ر.ي ${if (balYER > 0) "(عليه)" else "(له)"}", padding, y, paintLeftBold)
        y += 20f

        if (balSAR != 0.0) {
            canvas.drawText("الرصيد المتبقي (سعودي):", width - padding, y, paintRightBold)
            canvas.drawText("${String.format("%,.0f", balSAR)} ر.س ${if (balSAR > 0) "(عليه)" else "(له)"}", padding, y, paintLeftBold)
            y += 20f
        }

        canvas.drawLine(padding, y, width - padding, y, paintLine)
        y += 22f
        canvas.drawText("توقيع البائع: ..........", width - padding, y, paintNormal)
        canvas.drawText("توقيع العميل: ..........", padding, y, paintLeft)
        y += 30f

        canvas.drawText("شكراً لتعاملكم معنا — بقالة العزي", width / 2f, y, paintSub)
        y += 20f

        val finalHeight = y.toInt() + 10
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight.coerceAtMost(bitmap.height))
    }

    /**
     * Converts a black and white Bitmap into ESC/POS Raster bit image bytes (0x1D 0x76 0x30).
     */
    fun bitmapToEscPosBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = (width + 7) / 8

        val out = ByteArrayOutputStream()

        // Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))

        // ESC/POS command: GS v 0 (Print raster bit image)
        // 0x1D, 0x76, 0x30, mode(0), xL, xH, yL, yH
        val xL = (widthInBytes and 0xFF).toByte()
        val xH = ((widthInBytes shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        // Pixel raster data
        for (y in 0 until height) {
            for (xByte in 0 until widthInBytes) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (luminance < 128) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                }
                out.write(byteVal)
            }
        }

        // Feed paper and cut
        out.write(byteArrayOf(0x1B, 0x64, 0x04)) // Feed 4 lines
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // Paper cut (if supported)

        return out.toByteArray()
    }

    /**
     * Connects to a paired Bluetooth device and sends the print data asynchronously.
     */
    @SuppressLint("MissingPermission")
    fun printViaBluetooth(
        device: BluetoothDevice,
        data: ByteArray,
        onComplete: (Boolean, String) -> Unit
    ) {
        Executors.newSingleThreadExecutor().execute {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val os = socket.outputStream
                os.write(data)
                os.flush()
                onComplete(true, "تم إرسال الفاتورة بنجاح إلى ${device.name ?: "طابعة Bluetooth"}")
            } catch (e: Exception) {
                onComplete(false, "تعذرت الطباعة: ${e.localizedMessage ?: "تأكد من تشغيل الطابعة واقترانها"}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}
