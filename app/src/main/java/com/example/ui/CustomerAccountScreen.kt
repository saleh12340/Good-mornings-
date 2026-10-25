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
        }
    }
}

