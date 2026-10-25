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
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    customer: Customer,
    existingInvoice: TransactionEntry?,
    stockList: List<StockItem> = emptyList(),
    onBack: () -> Unit,
    onSaveInvoice: (TransactionEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var invoiceNumber by remember {
        mutableStateOf(
            existingInvoice?.invoiceNumber?.ifBlank { StoreRepository.generateNextInvoiceNumber(customer) }
                ?: StoreRepository.generateNextInvoiceNumber(customer)
        )
    }
    var invoiceDate by remember { mutableStateOf(existingInvoice?.date ?: getCurrentDateTime()) }
    var paymentType by remember { mutableStateOf(existingInvoice?.paymentType ?: PaymentType.CREDIT) }
    var currency by remember { mutableStateOf(existingInvoice?.currency ?: AppCurrency.YER) }
    var invoiceNote by remember { mutableStateOf(existingInvoice?.note ?: "") }

    val items = remember {
        mutableStateListOf<InvoiceItem>().apply {
            if (existingInvoice != null) addAll(existingInvoice.items)
        }
    }

    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("1") }
    var itemTotal by remember { mutableStateOf("") }
    var knownUnitPrice by remember { mutableStateOf<Double?>(null) }

    fun onQuantityChanged(newQtyStr: String) {
        itemQty = newQtyStr.filter { ch -> ch.isDigit() || ch == '.' }
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val unitP = knownUnitPrice
        if (unitP != null && unitP > 0) {
            itemTotal = String.format("%.0f", q * unitP)
        }
    }

    fun onTotalChanged(newTotalStr: String) {
        itemTotal = newTotalStr.filter { ch -> ch.isDigit() || ch == '.' }
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val t = itemTotal.toDoubleOrNull() ?: 0.0
        if (q > 0 && t > 0) {
            knownUnitPrice = t / q
        }
    }

    fun addItemToInvoice() {
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val t = itemTotal.toDoubleOrNull() ?: 0.0
        if (itemName.isBlank()) {
            Toast.makeText(context, "الرجاء كتابة اسم الصنف", Toast.LENGTH_SHORT).show()
            return
        }
        if (t <= 0) {
            Toast.makeText(context, "الرجاء إدخال القيمة الإجمالية للصنف", Toast.LENGTH_SHORT).show()
            return
        }
        val unitP = if (q > 0) t / q else t
        items.add(InvoiceItem(name = itemName.trim(), quantity = q, unitPrice = unitP, total = t))
        itemName = ""
        itemQty = "1"
        itemTotal = ""
        knownUnitPrice = null
    }

    var discountStr by remember { mutableStateOf(existingInvoice?.discount?.takeIf { it > 0 }?.toInt()?.toString() ?: "0") }
    var paidStr by remember { mutableStateOf(existingInvoice?.paid?.takeIf { it > 0 }?.toInt()?.toString() ?: "0") }

    val subtotal = items.sumOf { it.total }
    val discount = discountStr.toDoubleOrNull() ?: 0.0
    val netTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paid = paidStr.toDoubleOrNull() ?: 0.0
    val remaining = (netTotal - paid).coerceAtLeast(0.0)

    var showPrinterDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showPrinterDialog = true
        else Toast.makeText(context, "صلاحية Bluetooth مطلوبة للطباعة", Toast.LENGTH_SHORT).show()
    }

    fun currentInvoiceObject(): TransactionEntry {
        val id = existingInvoice?.id ?: UUID.randomUUID().toString()
        return TransactionEntry(
            id = id,
            invoiceNumber = invoiceNumber.trim(),
            title = "فاتورة $invoiceNumber",
            amount = netTotal,
            currency = currency,
            kind = EntryKind.INVOICE,
            paymentType = paymentType,
            date = invoiceDate,
            note = invoiceNote.trim(),
            items = items.toList(),
            discount = discount,
            paid = paid,
            remaining = remaining
        )
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
                        Text(if (existingInvoice == null) "فاتورة جديدة" else "تعديل فاتورة", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White)
                        Text("• ${customer.name}", fontSize = 11.5.sp, color = Color(0xFFC8E6C9), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    IconButton(
                        onClick = {
                            val inv = currentInvoiceObject()
                            val bmp = ThermalPrinter.createInvoiceBitmap(customer, inv, is80mm = false)
                            ShareUtils.shareImage(context, bmp, "inv_${inv.invoiceNumber}", customer.phone, "فاتورة مبيعات - ${customer.name}")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال واتساب", tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(BackgroundGray).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = invoiceNumber,
                                onValueChange = { invoiceNumber = it },
                                label = { Text("رقم الفاتورة", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = invoiceDate,
                                onValueChange = { invoiceDate = it },
                                label = { Text("التاريخ والوقت", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1.3f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            ) {
                                PaymentType.values().forEach { type ->
                                    val isSelected = paymentType == type
                                    Box(
                                        modifier = Modifier
                                            .clickable { paymentType = type }
                                            .background(if (isSelected) GreenPrimary else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(type.label, fontSize = 10.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else TextDark)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الخصم (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.9f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = paidStr,
                                onValueChange = { paidStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الواصل (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.9f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = invoiceNote,
                                onValueChange = { invoiceNote = it },
                                label = { Text("ملاحظة الفاتورة", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1.2f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("اسم الصنف *", fontSize = 10.sp) },
                                placeholder = { Text("سكر، أرز...", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1.5f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = itemQty,
                                onValueChange = { onQuantityChanged(it) },
                                label = { Text("الكمية", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.75f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = itemTotal,
                                onValueChange = { onTotalChanged(it) },
                                label = { Text("الإجمالي (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1.1f).height(58.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            Button(
                                onClick = { addItemToInvoice() },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(58.dp).padding(top = 2.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
