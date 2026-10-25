package com.example.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.StoreRepository
import com.example.model.*
import com.example.printer.ThermalPrinter
import com.example.ui.theme.*
import com.example.util.PdfAndPrintUtils
import com.example.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAccountScreen(
    customer: Customer,
    onBack: () -> Unit,
    onUpdateCustomer: (Customer) -> Unit,
    onOpenInvoiceBuilder: (TransactionEntry?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Quick Entry State
    var quickAmount by remember { mutableStateOf("") }
    var quickNote by remember { mutableStateOf("") }
    var quickDate by remember { mutableStateOf(getCurrentDateTime().split(" ")[0]) }
    var quickRef by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf(AppCurrency.YER) }

    // Dialogs
    var entryToEdit by remember { mutableStateOf<TransactionEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<TransactionEntry?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showShareMenuForStatement by remember { mutableStateOf(false) }

    var selectedEntryForOptions by remember { mutableStateOf<TransactionEntry?>(null) }

    var invoiceToPrint by remember { mutableStateOf<TransactionEntry?>(null) }
    var isPrintStatement by remember { mutableStateOf(false) }

    val (balYER, balSAR) = remember(customer.entries) {
        StoreRepository.calculateCustomerBalances(customer)
    }

    val ledger = remember(customer.entries) {
        StoreRepository.computeLedger(customer)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPrinterDialog = true
        } else {
            Toast.makeText(context, "صلاحية Bluetooth مطلوبة للطباعة", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBluetoothPrint(isStatement: Boolean, invoice: TransactionEntry? = null) {
        isPrintStatement = isStatement
        invoiceToPrint = invoice
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            showPrinterDialog = true
        }
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
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (customer.phone.isNotBlank()) {
                            Text(
                                text = "• ${customer.phone}",
                                fontSize = 11.sp,
                                color = Color(0xFFC8E6C9),
                                maxLines = 1
                            )
                        }
                    }

                    // WhatsApp Fast Direct Share
                    IconButton(
                        onClick = {
                            val text = StoreRepository.generateAccountStatementText(customer)
                            ShareUtils.shareTextToWhatsApp(context, customer.phone, text)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "إرسال واتساب",
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Options Menu for Bluetooth, PDF, Image, Share
                    Box {
                        IconButton(
                            onClick = { showShareMenuForStatement = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "خيارات الطباعة والمشاركة",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenuForStatement,
                            onDismissRequest = { showShareMenuForStatement = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🖨️ طباعة حرارية (Bluetooth)") },
                                onClick = {
                                    showShareMenuForStatement = false
                                    requestBluetoothPrint(isStatement = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 تصدير / طباعة PDF") },
                                onClick = {
                                    showShareMenuForStatement = false
                                    val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = true)
                                    PdfAndPrintUtils.printViaSystemManager(context, bmp, "كشف_حساب_${customer.name}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🖼️ مشاركة كشف الحساب كصورة") },
                                onClick = {
                                    showShareMenuForStatement = false
                                    val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                                    ShareUtils.shareImage(context, bmp, "statement_${customer.id.take(6)}", customer.phone, "كشف حساب بقالة العزي - ${customer.name}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("💬 إرسال واتساب مباشر") },
                                onClick = {
                                    showShareMenuForStatement = false
                                    val text = StoreRepository.generateAccountStatementText(customer)
                                    ShareUtils.shareTextToWhatsApp(context, customer.phone, text)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🔗 مشاركة نصية") },
                                onClick = {
                                    showShareMenuForStatement = false
                                    val text = StoreRepository.generateAccountStatementText(customer)
                                    ShareUtils.shareGeneralText(context, text)
                                }
                            )
                        }
                    }
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
            // Balance Card (YER & SAR)
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // YER Balance
                    Column {
                        Text("الرصيد باليمني (ر.ي)", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format("%,.0f ر.ي", balYER),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (balYER > 0) RedDebit else if (balYER < 0) GreenCredit else TextDark
                        )
                        Text(
                            text = if (balYER > 0) "عليه مديونية" else if (balYER < 0) "له رصيد دائن" else "خالص",
                            fontSize = 10.sp,
                            color = if (balYER > 0) RedDebit else if (balYER < 0) GreenCredit else TextMuted
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(40.dp), color = Color(0xFFE2E8F0))

                    // SAR Balance
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الرصيد بالسعودي (ر.س)", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format("%,.0f ر.س", balSAR),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (balSAR > 0) RedDebit else if (balSAR < 0) GreenCredit else TextDark
                        )
                        Text(
                            text = if (balSAR > 0) "عليه مديونية" else if (balSAR < 0) "له رصيد دائن" else "خالص",
                            fontSize = 10.sp,
                            color = if (balSAR > 0) RedDebit else if (balSAR < 0) GreenCredit else TextMuted
                        )
                    }
                }
            }

            // Quick Entry Card (له / عليه) مع مربعات الإدخال في صفوف جنب إلى جنب
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Currency Toggle & Detailed Invoice Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Currency Segment
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable { selectedCurrency = AppCurrency.YER }
                                    .background(if (selectedCurrency == AppCurrency.YER) GreenPrimary else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "يمني (ر.ي)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCurrency == AppCurrency.YER) Color.White else TextDark
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clickable { selectedCurrency = AppCurrency.SAR }
                                    .background(if (selectedCurrency == AppCurrency.SAR) AmberSAR else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "سعودي (ر.س)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCurrency == AppCurrency.SAR) Color.White else TextDark
                                )
                            }
                        }

                        // Detailed Invoice Button
                        TextButton(
                            onClick = { onOpenInvoiceBuilder(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, null, Modifier.size(16.dp), tint = GreenPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("فاتورة بأصناف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        }
                    }

                    // الصف الأول من مربعات الإدخال (جنب إلى جنب): المبلغ + التاريخ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = quickAmount,
                            onValueChange = { quickAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("المبلغ (${selectedCurrency.symbol}) *") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = quickDate,
                            onValueChange = { quickDate = it },
                            label = { Text("التاريخ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // الصف الثاني من مربعات الإدخال (جنب إلى جنب): البيان / التفاصيل + رقم السند / ملاحظة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = quickNote,
                            onValueChange = { quickNote = it },
                            label = { Text("البيان / التفاصيل") },
                            placeholder = { Text("مثال: سداد / شراء...") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = quickRef,
                            onValueChange = { quickRef = it },
                            label = { Text("رقم السند / ملاحظة") },
                            placeholder = { Text("اختياري") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // صف الأزرار (جنب إلى جنب): «له» (سداد) و «عليه» (دين)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // «له» Button (Green)
                        Button(
                            onClick = {
                                val amt = quickAmount.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    Toast.makeText(context, "الرجاء إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                                } else {
                                    val noteText = if (quickRef.isNotBlank()) {
                                        if (quickNote.isNotBlank()) "${quickNote.trim()} (${quickRef.trim()})" else quickRef.trim()
                                    } else quickNote.trim()

                                    val entryDate = if (quickDate.isNotBlank()) {
                                        val timePart = getCurrentDateTime().split(" ").getOrNull(1) ?: "12:00"
                                        if (quickDate.contains(" ")) quickDate else "$quickDate $timePart"
                                    } else getCurrentDateTime()

                                    val entry = TransactionEntry(
                                        title = if (quickNote.isNotBlank()) quickNote else "سند قبض / دفعة",
                                        amount = amt,
                                        currency = selectedCurrency,
                                        kind = EntryKind.CREDIT,
                                        date = entryDate,
                                        note = noteText
                                    )
                                    onUpdateCustomer(customer.copy(entries = customer.entries + entry))
                                    quickAmount = ""
                                    quickNote = ""
                                    quickRef = ""
                                    Toast.makeText(context, "تم قيد المبلغ (له)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenCredit),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("له (سداد)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // «عليه» Button (Red)
                        Button(
                            onClick = {
                                val amt = quickAmount.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    Toast.makeText(context, "الرجاء إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                                } else {
                                    val noteText = if (quickRef.isNotBlank()) {
                                        if (quickNote.isNotBlank()) "${quickNote.trim()} (${quickRef.trim()})" else quickRef.trim()
                                    } else quickNote.trim()

                                    val entryDate = if (quickDate.isNotBlank()) {
                                        val timePart = getCurrentDateTime().split(" ").getOrNull(1) ?: "12:00"
                                        if (quickDate.contains(" ")) quickDate else "$quickDate $timePart"
                                    } else getCurrentDateTime()

                                    val entry = TransactionEntry(
                                        title = if (quickNote.isNotBlank()) quickNote else "قيد دين مباشر",
                                        amount = amt,
                                        currency = selectedCurrency,
                                        kind = EntryKind.DEBIT,
                                        date = entryDate,
                                        note = noteText
                                    )
                                    onUpdateCustomer(customer.copy(entries = customer.entries + entry))
                                    quickAmount = ""
                                    quickNote = ""
                                    quickRef = ""
                                    Toast.makeText(context, "تم قيد الدين (عليه)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("عليه (دين)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Ledger List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("سجل المعاملات والعمليات (${customer.entries.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
            }

            // Ledger List
            if (ledger.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد عمليات مسجلة حتى الآن", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(ledger, key = { it.entry.id }) { item ->
                        LedgerCard(
                            item = item,
                            customerPhone = customer.phone,
                            onEdit = {
                                if (item.entry.kind == EntryKind.INVOICE) {
                                    onOpenInvoiceBuilder(item.entry)
                                } else {
                                    entryToEdit = item.entry
                                }
                            },
                            onDelete = { entryToDelete = item.entry },
                            onWhatsAppShare = {
                                val text = if (item.entry.kind == EntryKind.INVOICE) {
                                    StoreRepository.generateInvoiceText(customer, item.entry)
                                } else {
                                    "بقالة العزي للمواد الغذائية\nالعميل: ${customer.name}\n${item.entry.title}\nالمبلغ: ${String.format("%,.0f", item.entry.amount)} ${item.entry.currency.symbol}\nالتاريخ: ${item.entry.date}\nشكراً لتعاملكم معنا"
                                }
                                ShareUtils.shareTextToWhatsApp(context, customer.phone, text)
                            },
                            onImageShare = {
                                val bmp = if (item.entry.kind == EntryKind.INVOICE) {
                                    ThermalPrinter.createInvoiceBitmap(customer, item.entry, is80mm = false)
                                } else {
                                    val dummy = item.entry.copy(title = "سند عملية - ${item.entry.title}")
                                    ThermalPrinter.createInvoiceBitmap(customer, dummy, is80mm = false)
                                }
                                ShareUtils.shareImage(context, bmp, "receipt_${item.entry.id.take(6)}", customer.phone, "إيصال معاملة بقالة العزي - ${customer.name}")
                            },
                            onPdfPrint = {
                                val bmp = if (item.entry.kind == EntryKind.INVOICE) {
                                    ThermalPrinter.createInvoiceBitmap(customer, item.entry, is80mm = true)
                                } else {
                                    val dummy = item.entry.copy(title = "سند عملية - ${item.entry.title}")
                                    ThermalPrinter.createInvoiceBitmap(customer, dummy, is80mm = true)
                                }
                                PdfAndPrintUtils.printViaSystemManager(context, bmp, "معاملة_${item.entry.id.take(6)}")
                            },
                            onBluetoothPrint = {
                                if (item.entry.kind == EntryKind.INVOICE) {
                                    requestBluetoothPrint(isStatement = false, invoice = item.entry)
                                } else {
                                    requestBluetoothPrint(isStatement = true)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Transaction Dialog (مع حقول إدخال جنب إلى جنب)
    if (entryToEdit != null) {
        val entry = entryToEdit!!
        var editAmt by remember { mutableStateOf(entry.amount.toInt().toString()) }
        var editDate by remember { mutableStateOf(entry.date) }
        var editNote by remember { mutableStateOf(entry.note) }
        var editRef by remember { mutableStateOf("") }
        var editKind by remember { mutableStateOf(entry.kind) }
        var editCurrency by remember { mutableStateOf(entry.currency) }

        AlertDialog(
            onDismissRequest = { entryToEdit = null },
            title = { Text("تعديل المعاملة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // صف نوع القيد والعملة في صف واحد
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = editKind == EntryKind.CREDIT,
                                onClick = { editKind = EntryKind.CREDIT },
                                label = { Text("له (سداد)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = editKind == EntryKind.DEBIT,
                                onClick = { editKind = EntryKind.DEBIT },
                                label = { Text("عليه (دين)", fontSize = 11.sp) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = editCurrency == AppCurrency.YER,
                                onClick = { editCurrency = AppCurrency.YER },
                                label = { Text("ر.ي", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = editCurrency == AppCurrency.SAR,
                                onClick = { editCurrency = AppCurrency.SAR },
                                label = { Text("ر.س", fontSize = 11.sp) }
                            )
                        }
                    }

                    // الصف الأول من التعديل (جنب إلى جنب): المبلغ + التاريخ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = editAmt,
                            onValueChange = { editAmt = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("المبلغ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = { editDate = it },
                            label = { Text("التاريخ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف الثاني من التعديل (جنب إلى جنب): البيان + ملاحظة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = editNote,
                            onValueChange = { editNote = it },
                            label = { Text("البيان") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = editRef,
                            onValueChange = { editRef = it },
                            label = { Text("ملاحظة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = editAmt.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        val fullNote = if (editRef.isNotBlank()) {
                            if (editNote.isNotBlank()) "${editNote.trim()} (${editRef.trim()})" else editRef.trim()
                        } else editNote.trim()

                        val updated = entry.copy(
                            amount = amt,
                            date = editDate,
                            note = fullNote,
                            kind = editKind,
                            currency = editCurrency
                        )
                        onUpdateCustomer(customer.copy(entries = customer.entries.map { if (it.id == updated.id) updated else it }))
                        entryToEdit = null
                        Toast.makeText(context, "تم تعديل المعاملة وتحديث الرصيد", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذه المعاملة (${entry.title}) بمبلغ ${String.format("%,.0f", entry.amount)} ${entry.currency.symbol}؟ سيتم تعديل الرصيد فوراً.") },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCustomer(customer.copy(entries = customer.entries.filter { it.id != entry.id }))
                    entryToDelete = null
                    Toast.makeText(context, "تم حذف المعاملة وإعادة احتساب الرصيد", Toast.LENGTH_SHORT).show()
                }) {
                    Text("حذف", color = RedDebit)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Bluetooth Printer Selection Dialog
    if (showPrinterDialog) {
        BluetoothPrinterPicker(
            onDismiss = { showPrinterDialog = false },
            onDeviceSelected = { device ->
                showPrinterDialog = false
                Toast.makeText(context, "جاري إرسال الطباعة إلى ${device.name ?: "الطابعة"}...", Toast.LENGTH_SHORT).show()

                val bytes = if (!isPrintStatement && invoiceToPrint != null) {
                    val bmp = ThermalPrinter.createInvoiceBitmap(customer, invoiceToPrint!!, is80mm = false)
                    ThermalPrinter.bitmapToEscPosBytes(bmp)
                } else {
                    val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                    ThermalPrinter.bitmapToEscPosBytes(bmp)
                }

                ThermalPrinter.printViaBluetooth(device, bytes) { success, msg ->
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun LedgerCard(
    item: LedgerEntry,
    customerPhone: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWhatsAppShare: () -> Unit,
    onImageShare: () -> Unit,
    onPdfPrint: () -> Unit,
    onBluetoothPrint: () -> Unit
) {
    val entry = item.entry
    val isCredit = entry.kind == EntryKind.CREDIT
    val isInvoice = entry.kind == EntryKind.INVOICE

    val badgeColor = if (isCredit) GreenCredit else RedDebit
    val badgeText = when (entry.kind) {
        EntryKind.CREDIT -> "له"
        EntryKind.DEBIT -> "عليه"
        EntryKind.INVOICE -> "فاتورة"
    }

    val running = if (entry.currency == AppCurrency.SAR) item.runningBalanceSAR else item.runningBalanceYER

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeColor)
                    }
                    Text(
                        text = if (isInvoice && entry.invoiceNumber.isNotBlank()) entry.invoiceNumber else entry.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextDark
                    )
                }
                Text(
                    text = "${if (isCredit) "−" else "+"} ${String.format("%,.0f", entry.amount)} ${entry.currency.symbol}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = badgeColor
                )
            }

            // Date & Running Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.date, fontSize = 10.sp, color = TextMuted)
                Text(
                    text = "الرصيد: ${String.format("%,.0f", running)} ${entry.currency.symbol} ${if (running > 0) "(عليه)" else if (running < 0) "(له)" else "(خالص)"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running > 0) RedDebit else if (running < 0) GreenCredit else TextMuted
                )
            }

            if (entry.note.isNotBlank()) {
                Text("بيان: ${entry.note}", fontSize = 11.sp, color = Color(0xFF475569))
            }

            if (entry.items.isNotEmpty()) {
                Text("عدد الأصناف: ${entry.items.size} | الواصل: ${String.format("%,.0f", entry.paid)} | المتبقي: ${String.format("%,.0f", entry.remaining)}", fontSize = 10.sp, color = TextMuted)
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(top = 2.dp))

            // Action row with WhatsApp, Bluetooth, PDF, Image, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp Button
                TextButton(
                    onClick = onWhatsAppShare,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(3.dp))
                    Text("واتساب", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onImageShare, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Image, contentDescription = "صورة", Modifier.size(15.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onPdfPrint, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", Modifier.size(15.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onBluetoothPrint, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طابعة", Modifier.size(15.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", Modifier.size(15.dp), tint = TextMuted)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", Modifier.size(15.dp), tint = RedDebit)
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothPrinterPicker(
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val pairedDevices = remember {
        try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار طابعة Bluetooth (58/80مم)") },
        text = {
            if (adapter == null) {
                Text("هذا الجهاز لا يدعم البلوتوث.")
            } else if (pairedDevices.isEmpty()) {
                Text("لا توجد طابعات مقترنة. يرجى إقران الطابعة الحرارية في إعدادات الهاتف أولاً.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pairedDevices) { device ->
                        OutlinedButton(
                            onClick = { onDeviceSelected(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.name ?: "طابعة Bluetooth", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(device.address, fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
