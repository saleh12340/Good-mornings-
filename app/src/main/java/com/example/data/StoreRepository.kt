package com.example.data

import android.content.Context
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StoreRepository {
    private const val FILE_NAME = "alizzi_store_data.json"
    private const val STOCK_FILE_NAME = "alizzi_inventory_data.json"

    fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)
    fun getStockFile(context: Context): File = File(context.filesDir, STOCK_FILE_NAME)

    // Customer Persistence
    fun loadCustomers(context: Context): List<Customer> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val arr = JSONArray(content)
            val list = mutableListOf<Customer>()
            for (i in 0 until arr.length()) {
                list.add(Customer.fromJson(arr.getJSONObject(i)))
            }
            sortCustomersNewestFirst(list)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomers(context: Context, customers: List<Customer>) {
        try {
            val file = getFile(context)
            val arr = JSONArray()
            customers.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    // Inventory Persistence
    fun loadStock(context: Context): List<StockItem> {
        val file = getStockFile(context)
        if (!file.exists()) {
            // Seed with common grocery items if empty
            val initial = listOf(
                StockItem(name = "كيس أرز بسمتي 10 كجم", quantity = 25.0, unit = "كيس", salePrice = 12500.0, costPrice = 11000.0),
                StockItem(name = "كيس سكر 10 كجم", quantity = 40.0, unit = "كيس", salePrice = 9000.0, costPrice = 8200.0),
                StockItem(name = "زيت طبخ 4 لتر", quantity = 18.0, unit = "حبة", salePrice = 6500.0, costPrice = 5800.0),
                StockItem(name = "كرتون شاي كبوس 24", quantity = 12.0, unit = "كرتون", salePrice = 4500.0, costPrice = 3900.0),
                StockItem(name = "حليب بودرة مدهش 900جم", quantity = 30.0, unit = "علبة", salePrice = 3800.0, costPrice = 3300.0),
                StockItem(name = "كرتون تونة الغويزي", quantity = 15.0, unit = "كرتون", salePrice = 14000.0, costPrice = 12800.0)
            )
            saveStock(context, initial)
            return initial
        }
        return try {
            val content = file.readText()
            val arr = JSONArray(content)
            val list = mutableListOf<StockItem>()
            for (i in 0 until arr.length()) {
                list.add(StockItem.fromJson(arr.getJSONObject(i)))
            }
            sortStockNewestFirst(list)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveStock(context: Context, stock: List<StockItem>) {
        try {
            val file = getStockFile(context)
            val arr = JSONArray()
            stock.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    /**
     * Deducts sold items from stock. If item does not exist, automatically creates it in stock with negative quantity.
     */
    fun processInvoiceInventory(context: Context, invoiceItems: List<InvoiceItem>, currency: AppCurrency): List<StockItem> {
        val currentStock = loadStock(context).toMutableList()

        for (invItem in invoiceItems) {
            val trimmedName = invItem.name.trim()
            val existingIndex = currentStock.indexOfFirst { it.name.trim().equals(trimmedName, ignoreCase = true) }

            if (existingIndex >= 0) {
                val existing = currentStock[existingIndex]
                val updatedQty = existing.quantity - invItem.quantity
                currentStock[existingIndex] = existing.copy(
                    quantity = updatedQty,
                    salePrice = if (invItem.unitPrice > 0) invItem.unitPrice else existing.salePrice,
                    updatedAt = getCurrentDateTime()
                )
            } else {
                // Not in stock -> Add it with negative quantity as requested
                currentStock.add(
                    0,
                    StockItem(
                        name = trimmedName,
                        quantity = -invItem.quantity,
                        unit = "حبة",
                        salePrice = invItem.unitPrice,
                        costPrice = invItem.unitPrice * 0.85,
                        currency = currency,
                        updatedAt = getCurrentDateTime()
                    )
                )
            }
        }

        saveStock(context, currentStock)
        return sortStockNewestFirst(currentStock)
    }

    // Duplicate Prevention
    fun isCustomerDuplicate(existingCustomers: List<Customer>, name: String, excludeId: String? = null): Boolean {
        val clean = name.trim()
        return existingCustomers.any {
            it.id != excludeId && it.name.trim().equals(clean, ignoreCase = true)
        }
    }

    fun isStockItemDuplicate(existingStock: List<StockItem>, name: String, excludeId: String? = null): Boolean {
        val clean = name.trim()
        return existingStock.any {
            it.id != excludeId && it.name.trim().equals(clean, ignoreCase = true)
        }
    }

    // Sorting: Newest First Everywhere
    fun sortCustomersNewestFirst(customers: List<Customer>): List<Customer> {
        return customers.sortedWith(
            compareByDescending<Customer> { c ->
                c.entries.maxOfOrNull { it.date } ?: c.updatedAt
            }.thenByDescending { it.createdAt }
        )
    }

    fun sortStockNewestFirst(stock: List<StockItem>): List<StockItem> {
        return stock.sortedByDescending { it.updatedAt }
    }

    fun calculateCustomerBalances(customer: Customer): Pair<Double, Double> {
        var balanceYER = 0.0
        var balanceSAR = 0.0

        customer.entries.forEach { entry ->
            val netDelta = when (entry.kind) {
                EntryKind.DEBIT -> entry.amount // عليه (دين)
                EntryKind.CREDIT -> -entry.amount // له (سداد)
                EntryKind.INVOICE -> {
                    if (entry.remaining > 0) entry.remaining
                    else (entry.amount - entry.paid).coerceAtLeast(0.0)
                }
            }

            if (entry.currency == AppCurrency.SAR) {
                balanceSAR += netDelta
            } else {
                balanceYER += netDelta
            }
        }

        return Pair(balanceYER, balanceSAR)
    }

    fun computeLedger(customer: Customer): List<LedgerEntry> {
        // Chronological order (oldest to newest) to calculate running balance
        val sorted = customer.entries.sortedBy { it.date }
        var currentYER = 0.0
        var currentSAR = 0.0

        val ledger = mutableListOf<LedgerEntry>()

        for (entry in sorted) {
            val netDelta = when (entry.kind) {
                EntryKind.DEBIT -> entry.amount
                EntryKind.CREDIT -> -entry.amount
                EntryKind.INVOICE -> {
                    if (entry.remaining > 0) entry.remaining
                    else (entry.amount - entry.paid).coerceAtLeast(0.0)
                }
            }

            if (entry.currency == AppCurrency.SAR) {
                currentSAR += netDelta
            } else {
                currentYER += netDelta
            }

            ledger.add(LedgerEntry(entry, currentYER, currentSAR))
        }

        // Return descending (newest first) for UI display
        return ledger.reversed()
    }

    fun generateNextInvoiceNumber(customer: Customer): String {
        val count = customer.entries.count { it.kind == EntryKind.INVOICE } + 1
        return "INV-${String.format("%04d", count)}"
    }

    fun exportBackup(customers: List<Customer>, stock: List<StockItem> = emptyList()): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        val custArr = JSONArray()
        customers.forEach { custArr.put(it.toJson()) }
        root.put("customers", custArr)

        val stockArr = JSONArray()
        stock.forEach { stockArr.put(it.toJson()) }
        root.put("stock", stockArr)

        return root.toString(2)
    }

    fun importBackup(jsonString: String): Pair<List<Customer>?, List<StockItem>?> {
        return try {
            val root = JSONObject(jsonString)
            val custArr = root.optJSONArray("customers")
            val custList = custArr?.let {
                val list = mutableListOf<Customer>()
                for (i in 0 until it.length()) {
                    list.add(Customer.fromJson(it.getJSONObject(i)))
                }
                list
            }

            val stockArr = root.optJSONArray("stock")
            val stockList = stockArr?.let {
                val list = mutableListOf<StockItem>()
                for (i in 0 until it.length()) {
                    list.add(StockItem.fromJson(it.getJSONObject(i)))
                }
                list
            }

            Pair(custList, stockList)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    fun generateAccountStatementText(customer: Customer): String = buildString {
        val (balYER, balSAR) = calculateCustomerBalances(customer)
        appendLine("================================")
        appendLine("     بقالة العزي للمواد الغذائية     ")
        appendLine("      الحسابات والديون والمخزن      ")
        appendLine("           هاتف: 777123456           ")
        appendLine("================================")
        appendLine("كشف حساب العميل: ${customer.name}")
        if (customer.phone.isNotBlank()) appendLine("رقم الهاتف: ${customer.phone}")
        appendLine("تاريخ الكشف: ${getCurrentDateTime()}")
        appendLine("--------------------------------")
        val ledger = computeLedger(customer).reversed() // chronological for statement
        ledger.forEach { item ->
            val e = item.entry
            val kindText = when (e.kind) {
                EntryKind.CREDIT -> "له (سداد)"
                EntryKind.DEBIT -> "عليه (دين)"
                EntryKind.INVOICE -> "فاتورة ${e.invoiceNumber}"
            }
            val currSymbol = e.currency.symbol
            val running = if (e.currency == AppCurrency.SAR) item.runningBalanceSAR else item.runningBalanceYER
            appendLine("${e.date} | $kindText")
            appendLine("المبلغ: ${String.format("%,.0f", e.amount)} $currSymbol | الرصيد: ${String.format("%,.0f", running)} $currSymbol")
            if (e.note.isNotBlank()) appendLine("البيان: ${e.note}")
            appendLine("--------------------------------")
        }
        appendLine("الرصيد المتبقي الإجمالي:")
        if (balYER != 0.0) appendLine("• يمني: ${String.format("%,.0f", balYER)} ر.ي ${if (balYER > 0) "(عليه)" else "(له)"}")
        if (balSAR != 0.0) appendLine("• سعودي: ${String.format("%,.0f", balSAR)} ر.س ${if (balSAR > 0) "(عليه)" else "(له)"}")
        if (balYER == 0.0 && balSAR == 0.0) appendLine("• الحساب خالص تماماً (0 ر.ي)")
        appendLine("================================")
        appendLine("شكراً لتعاملكم مع بقالة العزي")
    }

    fun generateInvoiceText(customer: Customer, invoice: TransactionEntry): String = buildString {
        appendLine("================================")
        appendLine("     بقالة العزي للمواد الغذائية     ")
        appendLine("           هاتف: 777123456           ")
        appendLine("================================")
        appendLine("فاتورة مبيعات: ${invoice.invoiceNumber.ifBlank { invoice.title }}")
        appendLine("التاريخ: ${invoice.date}")
        appendLine("العميل: ${customer.name}")
        appendLine("نوع الدفع: ${invoice.paymentType.label}")
        appendLine("العملة: ${invoice.currency.symbol}")
        appendLine("--------------------------------")
        if (invoice.items.isNotEmpty()) {
            appendLine("الصنف           الكمية  السعر   الإجمالي")
            invoice.items.forEach { itm ->
                val name = itm.name.take(12).padEnd(12)
                appendLine("$name ${itm.quantity} × ${itm.unitPrice.toInt()} = ${itm.total.toInt()}")
            }
            appendLine("--------------------------------")
        }
        appendLine("الإجمالي: ${String.format("%,.0f", invoice.amount)} ${invoice.currency.symbol}")
        if (invoice.discount > 0) appendLine("الخصم: ${String.format("%,.0f", invoice.discount)} ${invoice.currency.symbol}")
        appendLine("الواصل (المدفوع): ${String.format("%,.0f", invoice.paid)} ${invoice.currency.symbol}")
        appendLine("المتبقي: ${String.format("%,.0f", invoice.remaining)} ${invoice.currency.symbol}")
        if (invoice.note.isNotBlank()) appendLine("ملاحظات: ${invoice.note}")
        appendLine("--------------------------------")
        appendLine("توقيع البائع: ............   توقيع العميل: ............")
        appendLine("================================")
        appendLine("شكراً لتسوقكم معنا!")
    }
}
