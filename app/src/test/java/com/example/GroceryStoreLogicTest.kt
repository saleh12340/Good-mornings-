package com.example

import com.example.data.StoreRepository
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroceryStoreLogicTest {

    @Test
    fun testCustomerBalanceCalculation() {
        val c = Customer(
            name = "علي الشميري",
            phone = "777000111",
            entries = listOf(
                TransactionEntry(
                    title = "فاتورة مواد غذائية",
                    amount = 10000.0,
                    currency = AppCurrency.YER,
                    kind = EntryKind.INVOICE,
                    paid = 4000.0,
                    remaining = 6000.0
                ),
                TransactionEntry(
                    title = "سند قبض",
                    amount = 2000.0,
                    currency = AppCurrency.YER,
                    kind = EntryKind.CREDIT
                ),
                TransactionEntry(
                    title = "شراء بالآجل",
                    amount = 500.0,
                    currency = AppCurrency.SAR,
                    kind = EntryKind.DEBIT
                )
            )
        )

        val (yer, sar) = StoreRepository.calculateCustomerBalances(c)
        // YER: 6000 remaining from invoice - 2000 payment = 4000
        assertEquals(4000.0, yer, 0.01)
        // SAR: 500 debit = 500
        assertEquals(500.0, sar, 0.01)
    }

    @Test
    fun testInvoiceItemCalculations() {
        val items = listOf(
            InvoiceItem(name = "كيس أرز 10 كجم", quantity = 2.0, unitPrice = 12000.0, total = 24000.0),
            InvoiceItem(name = "زيت طبخ 4 لتر", quantity = 1.0, unitPrice = 6500.0, total = 6500.0)
        )

        val subtotal = items.sumOf { it.total }
        assertEquals(30500.0, subtotal, 0.01)

        val discount = 500.0
        val net = subtotal - discount
        assertEquals(30000.0, net, 0.01)

        val paid = 10000.0
        val remaining = net - paid
        assertEquals(20000.0, remaining, 0.01)
    }

    @Test
    fun testRunningBalanceLedger() {
        val c = Customer(
            name = "سالم محمد",
            entries = listOf(
                TransactionEntry(
                    title = "قيد دين 1",
                    amount = 5000.0,
                    currency = AppCurrency.YER,
                    kind = EntryKind.DEBIT,
                    date = "2026-10-24 10:00"
                ),
                TransactionEntry(
                    title = "قيد دين 2",
                    amount = 3000.0,
                    currency = AppCurrency.YER,
                    kind = EntryKind.DEBIT,
                    date = "2026-10-24 10:30"
                ),
                TransactionEntry(
                    title = "سداد (له)",
                    amount = 4000.0,
                    currency = AppCurrency.YER,
                    kind = EntryKind.CREDIT,
                    date = "2026-10-24 11:00"
                )
            )
        )

        val ledger = StoreRepository.computeLedger(c)
        assertEquals(3, ledger.size)

        // Ledger is returned descending (newest first)
        val newest = ledger[0] // 2026-10-24 11:00
        assertEquals(4000.0, newest.runningBalanceYER, 0.01) // 5000 + 3000 - 4000 = 4000

        val middle = ledger[1] // 2026-10-24 10:30
        assertEquals(8000.0, middle.runningBalanceYER, 0.01) // 5000 + 3000 = 8000

        val oldest = ledger[2] // 2026-10-24 10:00
        assertEquals(5000.0, oldest.runningBalanceYER, 0.01) // 5000
    }

    @Test
    fun testBackupExportAndImport() {
        val customers = listOf(
            Customer(name = "أحمد ناصر", phone = "771234567"),
            Customer(name = "عمر باحميد", phone = "733456789")
        )
        val stock = listOf(
            StockItem(name = "سكر السعيد 10 كجم", quantity = 15.0, salePrice = 9000.0)
        )

        val json = StoreRepository.exportBackup(customers, stock)
        assertNotNull(json)
        assertTrue(json.contains("أحمد ناصر"))
        assertTrue(json.contains("سكر السعيد"))

        val (importedCust, importedStock) = StoreRepository.importBackup(json)
        assertNotNull(importedCust)
        assertEquals(2, importedCust!!.size)
        assertEquals("أحمد ناصر", importedCust[0].name)
        assertEquals("عمر باحميد", importedCust[1].name)
        assertNotNull(importedStock)
        assertEquals(1, importedStock!!.size)
        assertEquals("سكر السعيد 10 كجم", importedStock[0].name)
    }

    @Test
    fun testDuplicateCustomerAndStockPrevention() {
        val customers = listOf(
            Customer(id = "1", name = "محمد عبده"),
            Customer(id = "2", name = "خالد قاسم")
        )
        assertTrue(StoreRepository.isCustomerDuplicate(customers, "محمد عبده"))
        assertTrue(StoreRepository.isCustomerDuplicate(customers, "  محمد عبده  "))
        assertFalse(StoreRepository.isCustomerDuplicate(customers, "محمد عبده", excludeId = "1"))
        assertFalse(StoreRepository.isCustomerDuplicate(customers, "سعيد صالح"))

        val stock = listOf(
            StockItem(id = "10", name = "حليب مدهش", quantity = 5.0)
        )
        assertTrue(StoreRepository.isStockItemDuplicate(stock, "حليب مدهش"))
        assertFalse(StoreRepository.isStockItemDuplicate(stock, "حليب مدهش", excludeId = "10"))
        assertFalse(StoreRepository.isStockItemDuplicate(stock, "شاي كبوس"))
    }

    @Test
    fun testWhatsAppPhoneNumberCleaning() {
        val yemenClean = com.example.util.ShareUtils.cleanPhoneNumber("777123456")
        assertEquals("967777123456", yemenClean)

        val yemenWithZero = com.example.util.ShareUtils.cleanPhoneNumber("0777123456")
        assertEquals("967777123456", yemenWithZero)

        val saudiClean = com.example.util.ShareUtils.cleanPhoneNumber("0551234567")
        assertEquals("966551234567", saudiClean)
    }
}
