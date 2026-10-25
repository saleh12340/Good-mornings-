package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.StoreRepository
import com.example.model.*
import com.example.printer.ThermalPrinter
import com.example.ui.theme.*
import com.example.util.PdfAndPrintUtils
import com.example.util.ShareUtils

data class CustomerTransaction(
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val entry: TransactionEntry
)

enum class FilterKind(val label: String) {
    ALL("الكل"),
    INVOICES("الفواتير"),
    CREDIT("قبض (له)"),
    DEBIT("دين (عليه)")
}

enum class FilterCurrency(val label: String) {
    ALL("كل العملات"),
    YER("ريال يمني"),
    SAR("ريال سعودي")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTransactionsScreen(
    customers: List<Customer>,
    onSelectCustomer: (String) -> Unit,
    onOpenInvoice: (customerId: String, invoice: TransactionEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(FilterKind.ALL) }
    var selectedCurrency by remember { mutableStateOf(FilterCurrency.ALL) }

    // Dialog & Printing states
    var entryForPrint by remember { mutableStateOf<CustomerTransaction?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPrinterDialog = true
        } else {
            Toast.makeText(context, "صلاحية Bluetooth مطلوبة للطباعة", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPrint(item: CustomerTransaction) {
        entryForPrint = item
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            showPrinterDialog = true
        }
    }

    // Flatten all entries and sort newest first
    val allTransactions = remember(customers) {
        customers.flatMap { c ->
            c.entries.map { entry ->
                CustomerTransaction(
                    customerId = c.id,
                    customerName = c.name,
                    customerPhone = c.phone,
                    entry = entry
                )
            }
        }.sortedByDescending { it.entry.date }
    }

    val filteredTransactions = remember(allTransactions, searchQuery, selectedKind, selectedCurrency) {
        allTransactions.filter { item ->
            // Search filter
            val queryMatch = searchQuery.isBlank() ||
                item.customerName.contains(searchQuery, ignoreCase = true) ||
                item.customerPhone.contains(searchQuery) ||
                item.entry.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                item.entry.title.contains(searchQuery, ignoreCase = true) ||
                item.entry.note.contains(searchQuery, ignoreCase = true)

            // Kind filter
            val kindMatch = when (selectedKind) {
                FilterKind.ALL -> true
                FilterKind.INVOICES -> item.entry.kind == EntryKind.INVOICE
                FilterKind.CREDIT -> item.entry.kind == EntryKind.CREDIT
                FilterKind.DEBIT -> item.entry.kind == EntryKind.DEBIT
            }

            // Currency filter
            val currMatch = when (selectedCurrency) {
                FilterCurrency.ALL -> true
                FilterCurrency.YER -> item.entry.currency == AppCurrency.YER
                FilterCurrency.SAR -> item.entry.currency == AppCurrency.SAR
            }

            queryMatch && kindMatch && currMatch
        }
    }

    // Totals for filtered transactions
    val totalYER = remember(filteredTransactions) {
        filteredTransactions.filter { it.entry.currency == AppCurrency.YER }.sumOf { it.entry.amount }
    }
    val totalSAR = remember(filteredTransactions) {
        filteredTransactions.filter { it.entry.currency == AppCurrency.SAR }.sumOf { it.entry.amount }
    }

    Scaffold(
        topBar = {
            Surface(
                color = GreenPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "سجل الفواتير والعمليات (الأحدث أولاً)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color.White
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundGray)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Totals Summary Card
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = GreenLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("إجمالي العمليات (يمني)", fontSize = 10.sp, color = GreenSecondary)
                        Text(String.format("%,.0f ر.ي", totalYER), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
                    }

                    VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xFFA5D6A7))

                    Column {
                        Text("إجمالي العمليات (سعودي)", fontSize = 10.sp, color = GreenSecondary)
                        Text(String.format("%,.0f ر.س", totalSAR), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AmberSAR)
                    }

                    VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xFFA5D6A7))

                    Column(horizontalAlignment = Alignment.End) {
                        Text("العدد", fontSize = 10.sp, color = GreenSecondary)
                        Text("${filteredTransactions.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث باسم العميل أو رقم الفاتورة أو البيان...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = GreenPrimary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            // Filter Chips (صفوف جنب إلى جنب)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // صف نوع العملية
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterKind.values().forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = { Text(kind.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // صف نوع العملة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterCurrency.values().forEach { curr ->
                        FilterChip(
                            selected = selectedCurrency == curr,
                            onClick = { selectedCurrency = curr },
                            label = { Text(curr.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Transactions List (الأحدث أولاً)
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedKind != FilterKind.ALL) "لا توجد عمليات مطابقة للفلاتر" else "لم يتم تسجيل أي فواتير أو سندات بعد",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    items(filteredTransactions, key = { "${it.customerId}_${it.entry.id}" }) { item ->
                        GlobalTransactionCard(
                            item = item,
                            onClick = {
                                if (item.entry.kind == EntryKind.INVOICE) {
                                    onOpenInvoice(item.customerId, item.entry)
                                } else {
                                    onSelectCustomer(item.customerId)
                                }
                            },
                            onWhatsApp = {
                                val cust = customers.find { it.id == item.customerId }
                                if (cust != null && cust.phone.isNotBlank()) {
                                    if (item.entry.kind == EntryKind.INVOICE) {
                                        val text = StoreRepository.generateInvoiceText(cust, item.entry)
                                        ShareUtils.shareTextToWhatsApp(context, cust.phone, text)
                                    } else {
                                        val text = buildString {
                                            appendLine("بقالة العزي للمواد الغذائية")
                                            appendLine("سند عملية: ${item.entry.title}")
                                            appendLine("العميل: ${cust.name}")
                                            appendLine("التاريخ: ${item.entry.date}")
                                            appendLine("المبلغ: ${String.format("%,.0f", item.entry.amount)} ${item.entry.currency.symbol}")
                                            if (item.entry.note.isNotBlank()) appendLine("البيان: ${item.entry.note}")
                                        }
                                        ShareUtils.shareTextToWhatsApp(context, cust.phone, text)
                                    }
                                } else {
                                    Toast.makeText(context, "لا يوجد رقم هاتف مسجل للعميل", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPrint = {
                                requestPrint(item)
                            }
                        )
                    }
                }
            }
        }
    }

    // Bluetooth Printer Dialog
    if (showPrinterDialog && entryForPrint != null) {
        val item = entryForPrint!!
        val customer = customers.find { it.id == item.customerId }
        if (customer != null) {
            BluetoothPrinterPicker(
                onDismiss = {
                    showPrinterDialog = false
                    entryForPrint = null
                },
                onDeviceSelected = { device ->
                    showPrinterDialog = false
                    Toast.makeText(context, "جاري إرسال الطباعة إلى ${device.name ?: "الطابعة"}...", Toast.LENGTH_SHORT).show()
                    val bmp = if (item.entry.kind == EntryKind.INVOICE) {
                        ThermalPrinter.createInvoiceBitmap(customer, item.entry, is80mm = false)
                    } else {
                        ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                    }
                    val bytes = ThermalPrinter.bitmapToEscPosBytes(bmp)
                    ThermalPrinter.printViaBluetooth(device, bytes) { success, msg ->
                        (context as? android.app.Activity)?.runOnUiThread {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    entryForPrint = null
                }
            )
        }
    }
}

@Composable
fun GlobalTransactionCard(
    item: CustomerTransaction,
    onClick: () -> Unit,
    onWhatsApp: () -> Unit,
    onPrint: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val entry = item.entry

    val kindColor = when (entry.kind) {
        EntryKind.INVOICE -> AmberSAR
        EntryKind.CREDIT -> GreenCredit
        EntryKind.DEBIT -> RedDebit
    }

    val kindBadge = when (entry.kind) {
        EntryKind.INVOICE -> "فاتورة (${entry.invoiceNumber.ifBlank { "مبيعات" }})"
        EntryKind.CREDIT -> "سند قبض / له (واصل)"
        EntryKind.DEBIT -> "دين / عليه"
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Kind Badge + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = kindColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = kindBadge,
                        color = kindColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = entry.date,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            // Customer Name + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                    if (entry.note.isNotBlank()) {
                        Text(
                            text = entry.note,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                    if (entry.kind == EntryKind.INVOICE && entry.items.isNotEmpty()) {
                        Text(
                            text = "${entry.items.size} أصناف: " + entry.items.take(3).joinToString("، ") { it.name } + if (entry.items.size > 3) "..." else "",
                            fontSize = 11.sp,
                            color = GreenSecondary,
                            maxLines = 1
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%,.0f", entry.amount)} ${entry.currency.symbol}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = kindColor
                    )
                    if (entry.kind == EntryKind.INVOICE && entry.remaining > 0) {
                        Text(
                            text = "المتبقي: ${String.format("%,.0f", entry.remaining)}",
                            fontSize = 10.sp,
                            color = RedDebit,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.paymentType != null) {
                    Text(
                        text = "الدفع: ${entry.paymentType.label}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // WhatsApp Share
                    if (item.customerPhone.isNotBlank()) {
                        IconButton(onClick = onWhatsApp, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Send, contentDescription = "مشاركة واتساب", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Print / Share Options
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Print, contentDescription = "خيارات الطباعة", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🖨️ طباعة حرارية (Bluetooth)") },
                                onClick = {
                                    showMenu = false
                                    onPrint()
                                }
                            )
                            if (entry.kind == EntryKind.INVOICE) {
                                DropdownMenuItem(
                                    text = { Text("📄 طباعة نظام / PDF") },
                                    onClick = {
                                        showMenu = false
                                        val cust = Customer(name = item.customerName, phone = item.customerPhone)
                                        val bmp = ThermalPrinter.createInvoiceBitmap(cust, entry, is80mm = true)
                                        PdfAndPrintUtils.printViaSystemManager(context, bmp, "فاتورة_${entry.invoiceNumber}")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🖼️ مشاركة الفاتورة كصورة") },
                                    onClick = {
                                        showMenu = false
                                        val cust = Customer(name = item.customerName, phone = item.customerPhone)
                                        val bmp = ThermalPrinter.createInvoiceBitmap(cust, entry, is80mm = false)
                                        ShareUtils.shareImage(context, bmp, "inv_${entry.invoiceNumber}", item.customerPhone, "فاتورة ${entry.invoiceNumber}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
