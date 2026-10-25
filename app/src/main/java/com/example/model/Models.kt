package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class AppCurrency(val code: String, val symbol: String) {
    YER("YER", "ر.ي"),
    SAR("SAR", "ر.س");

    companion object {
        fun fromCode(code: String): AppCurrency =
            if (code.equals("SAR", ignoreCase = true)) SAR else YER
    }
}

enum class EntryKind(val code: String, val label: String) {
    CREDIT("credit", "له (سداد)"),
    DEBIT("debit", "عليه (دين)"),
    INVOICE("invoice", "فاتورة مبيعات");

    companion object {
        fun fromCode(code: String): EntryKind = when (code) {
            "credit", "payment" -> CREDIT
            "debit", "debt" -> DEBIT
            else -> INVOICE
        }
    }
}

enum class PaymentType(val label: String) {
    CASH("نقد"),
    CREDIT("آجل"),
    TRANSFER("تحويل");

    companion object {
        fun fromLabel(label: String): PaymentType = when (label) {
            "نقد" -> CASH
            "تحويل" -> TRANSFER
            else -> CREDIT
        }
    }
}

data class InvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val total: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("quantity", quantity)
        put("unitPrice", unitPrice)
        put("total", total)
    }

    companion object {
        fun fromJson(obj: JSONObject): InvoiceItem = InvoiceItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            quantity = obj.optDouble("quantity", 1.0),
            unitPrice = obj.optDouble("unitPrice", 0.0),
            total = obj.optDouble("total", 0.0)
        )
    }
}

data class TransactionEntry(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String = "",
    val title: String,
    val amount: Double,
    val currency: AppCurrency = AppCurrency.YER,
    val kind: EntryKind = EntryKind.DEBIT,
    val paymentType: PaymentType = PaymentType.CREDIT,
    val date: String = getCurrentDateTime(),
    val note: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val discount: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("invoiceNumber", invoiceNumber)
        put("title", title)
        put("amount", amount)
        put("currency", currency.code)
        put("kind", kind.code)
        put("paymentType", paymentType.label)
        put("date", date)
        put("note", note)
        put("discount", discount)
        put("paid", paid)
        put("remaining", remaining)
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        put("items", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): TransactionEntry {
            val itemsArr = obj.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<InvoiceItem>()
            for (i in 0 until itemsArr.length()) {
                list.add(InvoiceItem.fromJson(itemsArr.getJSONObject(i)))
            }
            return TransactionEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                invoiceNumber = obj.optString("invoiceNumber", ""),
                title = obj.optString("title", ""),
                amount = obj.optDouble("amount", 0.0),
                currency = AppCurrency.fromCode(obj.optString("currency", "YER")),
                kind = EntryKind.fromCode(obj.optString("kind", "debit")),
                paymentType = PaymentType.fromLabel(obj.optString("paymentType", "آجل")),
                date = obj.optString("date", getCurrentDateTime()),
                note = obj.optString("note", ""),
                items = list,
                discount = obj.optDouble("discount", 0.0),
                paid = obj.optDouble("paid", 0.0),
                remaining = obj.optDouble("remaining", 0.0)
            )
        }
    }
}

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val note: String = "",
    val entries: List<TransactionEntry> = emptyList(),
    val createdAt: String = getCurrentDateTime(),
    val updatedAt: String = getCurrentDateTime()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phone", phone)
        put("note", note)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        put("entries", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): Customer {
            val entriesArr = obj.optJSONArray("entries") ?: JSONArray()
            val list = mutableListOf<TransactionEntry>()
            for (i in 0 until entriesArr.length()) {
                list.add(TransactionEntry.fromJson(entriesArr.getJSONObject(i)))
            }
            return Customer(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", ""),
                phone = obj.optString("phone", ""),
                note = obj.optString("note", ""),
                entries = list,
                createdAt = obj.optString("createdAt", getCurrentDateTime()),
                updatedAt = obj.optString("updatedAt", getCurrentDateTime())
            )
        }
    }
}

data class StockItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double,
    val unit: String = "حبة",
    val salePrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val currency: AppCurrency = AppCurrency.YER,
    val updatedAt: String = getCurrentDateTime()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("quantity", quantity)
        put("unit", unit)
        put("salePrice", salePrice)
        put("costPrice", costPrice)
        put("currency", currency.code)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): StockItem = StockItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            quantity = obj.optDouble("quantity", 0.0),
            unit = obj.optString("unit", "حبة"),
            salePrice = obj.optDouble("salePrice", 0.0),
            costPrice = obj.optDouble("costPrice", 0.0),
            currency = AppCurrency.fromCode(obj.optString("currency", "YER")),
            updatedAt = obj.optString("updatedAt", getCurrentDateTime())
        )
    }
}

data class LedgerEntry(
    val entry: TransactionEntry,
    val runningBalanceYER: Double,
    val runningBalanceSAR: Double
)

fun getCurrentDateTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date())
}

fun formatAmount(amount: Double, currency: AppCurrency): String {
    return String.format(Locale.getDefault(), "%,.0f %s", amount, currency.symbol)
}
