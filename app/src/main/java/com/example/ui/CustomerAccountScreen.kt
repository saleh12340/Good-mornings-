package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
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

    var quickAmount by remember { mutableStateOf("") }
    var quickNote by remember { mutableStateOf("") }
    var quickDate by remember { mutableStateOf(getCurrentDateTime().split(" ")[0]) }
    var quickRef by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf(AppCurrency.YER) }
    var showShareMenuForStatement by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var invoiceToPrint by remember { mutableStateOf<TransactionEntry?>(null) }
    var isPrintStatement by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<TransactionEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<TransactionEntry?>(null) }

    val (balYER, balSAR) = remember(customer.entries) { StoreRepository.calculateCustomerBalances(customer) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showPrinterDialog = true
        else Toast.makeText(context, "صلاحية Bluetooth مطلوبة للطباعة", Toast.LENGTH_SHORT).show()
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
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Row(
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                        if (customer.phone.isNotBlank()) {
                            Text("• ${customer.phone}", fontSize = 11.sp, color = Color(0xFFC8E6C9), maxLines = 1)
                        }
                    }

                    IconButton(
                        onClick = {
                            val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                            ShareUtils.shareImage(context, bmp, "statement_${customer.id.take(6)}", customer.phone, "كشف حساب بقالة العزي - ${customer.name}")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال واتساب", tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                    }

                    Box {
                        IconButton(onClick = { showShareMenuForStatement = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات الطباعة والمشاركة", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showShareMenuForStatement,
                            onDismissRequest = { showShareMenuForStatement = false }
                        ) {
                            DropdownMenuItem(text = { Text("🖨️ طباعة حرارية (Bluetooth)") }, onClick = {
                                showShareMenuForStatement = false
                                requestBluetoothPrint(isStatement = true)
                            })
                            DropdownMenuItem(text = { Text("📄 تصدير / طباعة PDF") }, onClick = {
                                showShareMenuForStatement = false
                                val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = true)
                                PdfAndPrintUtils.printViaSystemManager(context, bmp, "كشف_حساب_${customer.name}")
                            })
                            DropdownMenuItem(text = { Text("🖼️ مشاركة كشف الحساب كصورة") }, onClick = {
                                showShareMenuForStatement = false
                                val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                                ShareUtils.shareImage(context, bmp, "statement_${customer.id.take(6)}", customer.phone, "كشف حساب بقالة العزي - ${customer.name}")
                            })
                            DropdownMenuItem(text = { Text("💬 إرسال واتساب مباشر") }, onClick = {
                                showShareMenuForStatement = false
                                val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                                ShareUtils.shareImage(context, bmp, "statement_${customer.id.take(6)}", customer.phone, "كشف حساب بقالة العزي - ${customer.name}")
                            })
                            DropdownMenuItem(text = { Text("🔗 مشاركة نصية") }, onClick = {
                                showShareMenuForStatement = false
                                val text = StoreRepository.generateAccountStatementText(customer)
                                ShareUtils.shareGeneralText(context, text)
                            })
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(BackgroundGray).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الرصيد باليمني (ر.ي)", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format("%,.0f ر.ي", balYER),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (balYER > 0) RedDebit else if (balYER < 0) GreenCredit else TextDark
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(40.dp), color = Color(0xFFE2E8F0))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الرصيد بالسعودي (ر.س)", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format("%,.0f ر.س", balSAR),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (balSAR > 0) RedDebit else if (balSAR < 0) GreenCredit else TextDark
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable { selectedCurrency = AppCurrency.YER }
                                    .background(if (selectedCurrency == AppCurrency.YER) GreenPrimary else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("يمني (ر.ي)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedCurrency == AppCurrency.YER) Color.White else TextDark)
                            }
                            Box(
                                modifier = Modifier
                                    .clickable { selectedCurrency = AppCurrency.SAR }
                                    .background(if (selectedCurrency == AppCurrency.SAR) AmberSAR else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("سعودي (ر.س)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedCurrency == AppCurrency.SAR) Color.White else TextDark)
                            }
                        }

                        TextButton(
                            onClick = { onOpenInvoiceBuilder(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, null, Modifier.size(16.dp), tint = GreenPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("فاتورة بأصناف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        }
                    }

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
                            modifier = Modifier.weight(1.1f).height(58.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )

                        OutlinedTextField(
                            value = quickDate,
                            onValueChange = { quickDate = it },
                            label = { Text("التاريخ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )
                    }

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
                            modifier = Modifier.weight(1.3f).height(58.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )

                        OutlinedTextField(
                            value = quickRef,
                            onValueChange = { quickRef = it },
                            label = { Text("رقم السند / ملاحظة") },
                            placeholder = { Text("اختياري") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenCredit),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("له (سداد)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

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
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("عليه (دين)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            val ledger = remember(customer.entries) { StoreRepository.computeLedger(customer) }

            // Ledger Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        "سجل الحركات والعمليات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                }
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${ledger.size} حركة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (ledger.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Text("لا توجد حركات مسجلة لهذا العميل حتى الآن", color = Color.Gray, fontSize = 13.sp)
                        Text("يمكنك قيد دين أو دفعة عبر النموذج أعلاه، أو إنشاء فاتورة بأصناف", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(ledger, key = { it.entry.id }) { item ->
                        LedgerCard(
                            item = item,
                            onEdit = {
                                if (item.entry.kind == EntryKind.INVOICE) {
                                    onOpenInvoiceBuilder(item.entry)
                                } else {
                                    entryToEdit = item.entry
                                }
                            },
                            onDelete = {
                                entryToDelete = item.entry
                            },
                            onPrint = {
                                requestBluetoothPrint(isStatement = false, invoice = item.entry)
                            },
                            onShareWhatsApp = {
                                if (item.entry.kind == EntryKind.INVOICE) {
                                    val bmp = ThermalPrinter.createInvoiceBitmap(customer, item.entry, is80mm = false)
                                    ShareUtils.shareImage(context, bmp, "inv_${item.entry.invoiceNumber}", customer.phone, "فاتورة ${item.entry.invoiceNumber} - ${customer.name}")
                                } else {
                                    val msg = "سند بقالة العزي\nالعميل: ${customer.name}\n${item.entry.kind.label}: ${formatAmount(item.entry.amount, item.entry.currency)}\nالتاريخ: ${item.entry.date}\nالبيان: ${item.entry.note}"
                                    ShareUtils.shareTextToWhatsApp(context, customer.phone, msg)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Transaction Dialog
    if (entryToEdit != null) {
        val entry = entryToEdit!!
        var editTitle by remember(entry) { mutableStateOf(entry.title) }
        var editAmount by remember(entry) { mutableStateOf(entry.amount.toString()) }
        var editDate by remember(entry) { mutableStateOf(entry.date) }
        var editNote by remember(entry) { mutableStateOf(entry.note) }
        var editCurrency by remember(entry) { mutableStateOf(entry.currency) }
        var editKind by remember(entry) { mutableStateOf(entry.kind) }

        AlertDialog(
            onDismissRequest = { entryToEdit = null },
            title = {
                Text("تعديل الحركة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = editKind == EntryKind.DEBIT,
                            onClick = { editKind = EntryKind.DEBIT },
                            label = { Text("عليه (دين)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = editKind == EntryKind.CREDIT,
                            onClick = { editKind = EntryKind.CREDIT },
                            label = { Text("له (سداد)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("المبلغ (${editCurrency.symbol}) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("عنوان الحركة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("التاريخ والوقت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("البيان والملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = editAmount.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            Toast.makeText(context, "الرجاء إدخال مبلغ صحيح", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedEntry = entry.copy(
                                title = editTitle.ifBlank { if (editKind == EntryKind.CREDIT) "سند قبض" else "مشتريات" },
                                amount = amt,
                                currency = editCurrency,
                                kind = editKind,
                                date = editDate,
                                note = editNote
                            )
                            val updatedEntries = customer.entries.map { if (it.id == updatedEntry.id) updatedEntry else it }
                            onUpdateCustomer(customer.copy(entries = updatedEntries))
                            entryToEdit = null
                            Toast.makeText(context, "تم حفظ تعديل الحركة", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
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
            title = {
                Text("تأكيد الحذف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("هل أنت متأكد من حذف هذه الحركة؟\n${entry.title} بمبلغ ${formatAmount(entry.amount, entry.currency)}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedEntries = customer.entries.filter { it.id != entry.id }
                        onUpdateCustomer(customer.copy(entries = updatedEntries))
                        entryToDelete = null
                        Toast.makeText(context, "تم حذف الحركة بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Bluetooth Printer Picker
    if (showPrinterDialog) {
        BluetoothPrinterPicker(
            onDismiss = { showPrinterDialog = false },
            onDeviceSelected = { device ->
                showPrinterDialog = false
                val bmp = if (isPrintStatement) {
                    ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                } else {
                    val entry = invoiceToPrint ?: customer.entries.firstOrNull()
                    if (entry != null && entry.kind == EntryKind.INVOICE) {
                        ThermalPrinter.createInvoiceBitmap(customer, entry, is80mm = false)
                    } else {
                        ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                    }
                }
                val bytes = ThermalPrinter.bitmapToEscPosBytes(bmp)
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val entry = item.entry
    val isDebit = entry.kind == EntryKind.DEBIT
    val isCredit = entry.kind == EntryKind.CREDIT
    val isInvoice = entry.kind == EntryKind.INVOICE

    val kindColor = when {
        isCredit -> GreenCredit
        isDebit -> RedDebit
        else -> Color(0xFF1E88E5)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Tag, Date, and Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = kindColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isInvoice && entry.invoiceNumber.isNotBlank()) "فاتورة ${entry.invoiceNumber}" else entry.kind.label,
                            color = kindColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.date,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onPrint, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShareWhatsApp, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "واتساب", tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Main details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank { entry.kind.label },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = TextDark,
                        maxLines = 1
                    )
                    if (entry.note.isNotBlank() && entry.note != entry.title) {
                        Text(
                            text = entry.note,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 2
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isCredit) "-" else "+"
                    Text(
                        text = "$sign${formatAmount(entry.amount, entry.currency)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = kindColor
                    )
                    val runningBal = if (entry.currency == AppCurrency.SAR) item.runningBalanceSAR else item.runningBalanceYER
                    Text(
                        text = "الرصيد: ${formatAmount(runningBal, entry.currency)}",
                        fontSize = 11.sp,
                        color = if (runningBal > 0) RedDebit else if (runningBal < 0) GreenCredit else TextMuted
                    )
                }
            }

            // Items preview if invoice
            if (isInvoice && entry.items.isNotEmpty()) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "${entry.items.size} أصناف في الفاتورة:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        entry.items.take(3).forEach { itm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• ${itm.name} (${itm.quantity})", fontSize = 10.sp, color = TextMuted)
                                Text("${itm.total.toInt()} ${entry.currency.symbol}", fontSize = 10.sp, color = TextDark)
                            }
                        }
                        if (entry.items.size > 3) {
                            Text("...والمزيد (${entry.items.size - 3} أصناف)", fontSize = 9.5.sp, color = GreenPrimary)
                        }
                    }
                }
            }
        }
    }
}
