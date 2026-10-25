package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    var showCalculator by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var showShareMenuForStatement by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var invoiceToPrint by remember { mutableStateOf<TransactionEntry?>(null) }
    var isPrintStatement by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<TransactionEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<TransactionEntry?>(null) }
    var selectedEntryForActions by remember { mutableStateOf<TransactionEntry?>(null) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customer.phone.isNotBlank()) {
                                Text(
                                    text = customer.phone,
                                    fontSize = 11.sp,
                                    color = Color(0xFFC8E6C9),
                                    maxLines = 1
                                )
                            }
                        }

                        // Search Toggle Icon
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "بحث في الحركات",
                                tint = if (isSearchActive) Color(0xFFFFF176) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // PDF Statement Quick Export (Red Adobe PDF Icon style)
                        IconButton(
                            onClick = {
                                val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = true)
                                PdfAndPrintUtils.printViaSystemManager(context, bmp, "كشف_حساب_${customer.name}")
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "تصدير PDF",
                                tint = Color(0xFFFFCDD2),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // WhatsApp Direct Share
                        IconButton(
                            onClick = {
                                val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                                ShareUtils.shareImage(
                                    context,
                                    bmp,
                                    "statement_${customer.id.take(6)}",
                                    customer.phone,
                                    "كشف حساب بقالة العزي - ${customer.name}"
                                )
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "إرسال واتساب",
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Overflow Options
                        Box {
                            IconButton(
                                onClick = { showShareMenuForStatement = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "خيارات إضافية",
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
                                    text = { Text("💬 إرسال كشف الحساب عبر واتساب") },
                                    onClick = {
                                        showShareMenuForStatement = false
                                        val bmp = ThermalPrinter.createStatementBitmap(customer, is80mm = false)
                                        ShareUtils.shareImageToWhatsApp(
                                            context,
                                            bmp,
                                            "statement_${customer.id.take(6)}",
                                            customer.phone,
                                            "كشف حساب بقالة العزي - ${customer.name}\n${StoreRepository.generateAccountStatementText(customer)}"
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Optional Search Bar
                    if (isSearchActive) {
                        Surface(
                            color = Color(0xFF1B5E20),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث بالمبلغ أو البيان...", fontSize = 12.sp, color = Color(0xFFC8E6C9)) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color(0xFF81C784),
                                    focusedContainerColor = Color(0x33000000),
                                    unfocusedContainerColor = Color(0x22000000)
                                )
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
                .background(SoftBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Balance Summary Card (Soft Neumorphic Card)
            SoftCard(
                elevation = 1.5.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الرصيد باليمني (ر.ي)", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format("%,.0f ر.ي", balYER),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (balYER > 0) RedDebit else if (balYER < 0) GreenCredit else TextDark
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 8.dp),
                        color = Color(0xFFE2E8F0)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
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

            // 2. Quick Transaction Entry Card (Styling matching Screenshots 2 & 3)
            SoftCard(
                elevation = 2.dp,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Currency Toggle & Invoice Shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Soft Segmented Currency Selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftInputBg)
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
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

                        // Button to open full invoice builder
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { onOpenInvoiceBuilder(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(15.dp))
                                Text("فاتورة بأصناف", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            }
                        }
                    }

                    // Input Row 1: Amount (with embedded Calculator icon) & Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Amount with Calculator Icon inside trailingIcon
                        OutlinedTextField(
                            value = quickAmount,
                            onValueChange = { quickAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("المبلغ (${selectedCurrency.symbol}) *", fontSize = 11.sp) },
                            placeholder = { Text("0", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showCalculator = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = "آلة حاسبة سريعة",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(56.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = SoftInputBg
                            )
                        )

                        // Date field
                        OutlinedTextField(
                            value = quickDate,
                            onValueChange = { quickDate = it },
                            label = { Text("التاريخ", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = SoftInputBg
                            )
                        )
                    }

                    // Input Row 2: Note / Details & Quick Reference
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = quickNote,
                            onValueChange = { quickNote = it },
                            label = { Text("البيان / التفاصيل", fontSize = 11.sp) },
                            placeholder = { Text("مثال: مقاضي، سداد...", fontSize = 10.5.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(54.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = SoftInputBg
                            )
                        )

                        OutlinedTextField(
                            value = quickRef,
                            onValueChange = { quickRef = it },
                            label = { Text("رقم السند", fontSize = 11.sp) },
                            placeholder = { Text("اختياري", fontSize = 10.5.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(54.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FFFB),
                                unfocusedContainerColor = SoftInputBg
                            )
                        )
                    }

                    // Smart Quick Chips for Note/Description
                    val quickChips = listOf("مقاضي", "شقاره", "سداد نقدي", "حساب قديم", "بضاعة", "قات", "دفعة")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(quickChips) { chip ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (quickNote == chip) GreenLight else Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, if (quickNote == chip) GreenPrimary else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable { quickNote = chip }
                            ) {
                                Text(
                                    text = chip,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (quickNote == chip) FontWeight.Bold else FontWeight.Normal,
                                    color = if (quickNote == chip) GreenPrimary else TextDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Prominent Neumorphic Action Buttons: له ▲ vs عليه ▼ (Screenshot 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // له (سداد / دفعة) - Green button with upward arrow ▲
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
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenCredit),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "له", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("له (سداد)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // عليه (دين / مشتريات) - Red button with downward arrow ▼
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
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "عليه", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("عليه (دين)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // 3. Accounting Ledger Table Header (Clean table styling from Screenshot 2)
            val fullLedger = remember(customer.entries) { StoreRepository.computeLedger(customer) }
            val ledger = remember(fullLedger, searchQuery) {
                if (searchQuery.isBlank()) fullLedger
                else {
                    val q = searchQuery.trim().lowercase()
                    fullLedger.filter {
                        it.entry.title.lowercase().contains(q) ||
                        it.entry.note.lowercase().contains(q) ||
                        it.entry.amount.toString().contains(q) ||
                        it.entry.date.contains(q)
                    }
                }
            }

            // Table Header Bar
            Surface(
                color = Color(0xFFE2E8F0),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("التاريخ والوقت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.weight(1.3f))
                    Text("المبلغ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("التفاصيل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                    Text("نوع", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, textAlign = TextAlign.Center, modifier = Modifier.weight(0.4f))
                    Text("الرصيد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                }
            }

            if (ledger.isEmpty()) {
                SoftCard(
                    elevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "لا توجد حركات تطابق البحث" else "لا توجد حركات مسجلة لهذا العميل حتى الآن",
                            color = Color.Gray,
                            fontSize = 12.5.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(ledger, key = { it.entry.id }) { item ->
                        LedgerTableRow(
                            item = item,
                            isSelected = selectedEntryForActions?.id == item.entry.id,
                            onClick = {
                                selectedEntryForActions = if (selectedEntryForActions?.id == item.entry.id) null else item.entry
                            },
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

    // Quick Calculator Modal
    if (showCalculator) {
        QuickCalculatorDialog(
            initialValue = quickAmount,
            onDismiss = { showCalculator = false },
            onConfirm = { calculatedAmount ->
                quickAmount = if (calculatedAmount % 1.0 == 0.0) calculatedAmount.toLong().toString() else String.format("%.2f", calculatedAmount)
                showCalculator = false
            }
        )
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

/**
 * Clean Accounting Ledger Row matching Screenshot 2 table layout with expandable quick actions
 */
@Composable
fun LedgerTableRow(
    item: LedgerEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val entry = item.entry
    val isDebit = entry.kind == EntryKind.DEBIT
    val isCredit = entry.kind == EntryKind.CREDIT
    val isInvoice = entry.kind == EntryKind.INVOICE

    val runningBal = if (entry.currency == AppCurrency.SAR) item.runningBalanceSAR else item.runningBalanceYER

    Surface(
        color = if (isSelected) Color(0xFFF1F8E9) else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected) GreenPrimary else Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Main Ledger Table Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. التاريخ والوقت (e.g. 17-10-2026 13:02)
                Column(modifier = Modifier.weight(1.3f)) {
                    val parts = entry.date.split(" ")
                    Text(
                        text = parts.firstOrNull() ?: entry.date,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark
                    )
                    if (parts.size > 1) {
                        Text(
                            text = parts.drop(1).joinToString(" "),
                            fontSize = 9.5.sp,
                            color = TextMuted
                        )
                    }
                }

                // 2. المبلغ (e.g. 1,150)
                Text(
                    text = String.format("%,.0f", entry.amount),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) GreenCredit else RedDebit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // 3. التفاصيل / البيان (e.g. مقاضي)
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = entry.title.ifBlank { entry.kind.label },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.note.isNotBlank() && entry.note != entry.title) {
                        Text(
                            text = entry.note,
                            fontSize = 9.5.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 4. مؤشر الحركة (Red ▼ for عليه / Green ▲ for له)
                Box(
                    modifier = Modifier.weight(0.4f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCredit) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "له",
                            tint = GreenCredit,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "عليه",
                            tint = RedDebit,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // 5. الرصيد التراكمي
                Text(
                    text = String.format("%,.0f", runningBal),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (runningBal > 0) RedDebit else if (runningBal < 0) GreenCredit else TextDark,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.2f)
                )
            }

            // Quick actions drawer when tapped
            if (isSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = (if (isCredit) GreenCredit else RedDebit).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isInvoice && entry.invoiceNumber.isNotBlank()) "فاتورة ${entry.invoiceNumber}" else entry.kind.label,
                                color = if (isCredit) GreenCredit else RedDebit,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = entry.currency.symbol,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onPrint, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة", tint = TextDark, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onShareWhatsApp, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Send, contentDescription = "واتساب", tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = GreenPrimary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedDebit, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
