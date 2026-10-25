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
            if (existingInvoice != null) {
                addAll(existingInvoice.items)
            }
        }
    }

    // New item inputs: اسم الصنف - الكمية - القيمة الإجمالية (في صف واحد)
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("1") }
    var itemTotal by remember { mutableStateOf("") }

    // Last known unit price for auto-scaling when quantity changes
    var knownUnitPrice by remember { mutableStateOf<Double?>(null) }

    // سعر الواحدة مستنتج تلقائياً: القيمة الإجمالية ÷ الكمية
    val parsedQty = itemQty.toDoubleOrNull() ?: 1.0
    val parsedTotal = itemTotal.toDoubleOrNull() ?: 0.0
    val inferredUnitPrice = if (parsedQty > 0 && parsedTotal > 0) parsedTotal / parsedQty else (knownUnitPrice ?: 0.0)

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
        items.add(
            InvoiceItem(
                name = itemName.trim(),
                quantity = q,
                unitPrice = unitP,
                total = t
            )
        )
        // Reset inputs
        itemName = ""
        itemQty = "1"
        itemTotal = ""
        knownUnitPrice = null
    }

    // Discount and Paid inputs
    var discountStr by remember { mutableStateOf(existingInvoice?.discount?.takeIf { it > 0 }?.toInt()?.toString() ?: "0") }
    var paidStr by remember { mutableStateOf(existingInvoice?.paid?.takeIf { it > 0 }?.toInt()?.toString() ?: "0") }

    val subtotal = items.sumOf { it.total }
    val discount = discountStr.toDoubleOrNull() ?: 0.0
    val netTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paid = paidStr.toDoubleOrNull() ?: 0.0
    val remaining = (netTotal - paid).coerceAtLeast(0.0)

    // Printing and Sharing Dialogs
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPrinterDialog = true
        } else {
            Toast.makeText(context, "صلاحية Bluetooth مطلوبة للطباعة", Toast.LENGTH_SHORT).show()
        }
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
        // ترويسة صغيرة جداً تستغل المساحة بالكامل
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

                    // عنوان مدمج في سطر واحد
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (existingInvoice == null) "فاتورة جديدة" else "تعديل فاتورة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        Text(
                            text = "• ${customer.name}",
                            fontSize = 11.5.sp,
                            color = Color(0xFFC8E6C9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // إرسال واتساب سريع
                    IconButton(
                        onClick = {
                            val inv = currentInvoiceObject()
                            val text = StoreRepository.generateInvoiceText(customer, inv)
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

                    // قائمة خيارات الطباعة والمشاركة
                    Box {
                        IconButton(
                            onClick = { showShareMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "خيارات إضافية",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🖨️ طباعة حرارية (Bluetooth)") },
                                onClick = {
                                    showShareMenu = false
                                    if (Build.VERSION.SDK_INT >= 31 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    } else {
                                        showPrinterDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 تصدير / طباعة PDF") },
                                onClick = {
                                    showShareMenu = false
                                    val inv = currentInvoiceObject()
                                    val bmp = ThermalPrinter.createInvoiceBitmap(customer, inv, is80mm = true)
                                    PdfAndPrintUtils.printViaSystemManager(context, bmp, "فاتورة_${inv.invoiceNumber}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🖼️ مشاركة الفاتورة كصورة") },
                                onClick = {
                                    showShareMenu = false
                                    val inv = currentInvoiceObject()
                                    val bmp = ThermalPrinter.createInvoiceBitmap(customer, inv, is80mm = false)
                                    ShareUtils.shareImage(context, bmp, "inv_${inv.invoiceNumber}", customer.phone, "فاتورة مبيعات - ${customer.name}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("💬 إرسال واتساب مباشر") },
                                onClick = {
                                    showShareMenu = false
                                    val inv = currentInvoiceObject()
                                    val text = StoreRepository.generateInvoiceText(customer, inv)
                                    ShareUtils.shareTextToWhatsApp(context, customer.phone, text)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🔗 مشاركة نصية") },
                                onClick = {
                                    showShareMenu = false
                                    val inv = currentInvoiceObject()
                                    val text = StoreRepository.generateInvoiceText(customer, inv)
                                    ShareUtils.shareGeneralText(context, text)
                                }
                            )
                        }
                    }

                    // زر حفظ مضغوط وأنيق في الترويسة
                    Button(
                        onClick = {
                            if (items.isEmpty()) {
                                Toast.makeText(context, "الرجاء إضافة صنف واحد على الأقل للفاتورة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSaveInvoice(currentInvoiceObject())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberSAR),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .padding(start = 2.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundGray)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. بيانات الفاتورة المدمجة (رقم الفاتورة، التاريخ، طريقة السداد، العملة)
            item {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // صف رقم الفاتورة والتاريخ
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            OutlinedTextField(
                                value = invoiceDate,
                                onValueChange = { invoiceDate = it },
                                label = { Text("التاريخ والوقت", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }

                        // صف نوع الدفع والعملة في صف واحد مدمج جداً
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // نوع الدفع: آجل / نقداً
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
                                        Text(
                                            type.label,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else TextDark
                                        )
                                    }
                                }
                            }

                            // العملة: ر.ي / ر.س
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable { currency = AppCurrency.YER }
                                        .background(if (currency == AppCurrency.YER) GreenPrimary else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "ر.ي",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (currency == AppCurrency.YER) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currency == AppCurrency.YER) Color.White else TextDark
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clickable { currency = AppCurrency.SAR }
                                        .background(if (currency == AppCurrency.SAR) AmberSAR else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "ر.س",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (currency == AppCurrency.SAR) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currency == AppCurrency.SAR) Color.White else TextDark
                                    )
                                }
                            }
                        }

                        // صف مدخلات الخصم والواصل والملاحظة في أعلى الشاشة (جنب إلى جنب)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الخصم (${currency.symbol})", fontSize = 10.sp) },
                                placeholder = { Text("0", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = paidStr,
                                onValueChange = { paidStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الواصل (${currency.symbol})", fontSize = 10.sp) },
                                placeholder = { Text("0", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(4.dp)
                            )

                            OutlinedTextField(
                                value = invoiceNote,
                                onValueChange = { invoiceNote = it },
                                label = { Text("ملاحظة الفاتورة", fontSize = 10.sp) },
                                placeholder = { Text("اختياري", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }
                }
            }

            // 2. بطاقة إدخال الصنف: [اسم الصنف] [الكمية] [القيمة الإجمالية] في صف واحد + سعر الواحدة مستنتج
            item {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // اقتراحات سريعة ومضغوطة من المخزن
                        val matchingStock = remember(itemName, stockList) {
                            if (itemName.isBlank()) stockList.take(6)
                            else stockList.filter { it.name.contains(itemName.trim(), ignoreCase = true) }.take(6)
                        }
                        if (matchingStock.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(matchingStock) { stock ->
                                    val isDeficit = stock.quantity < 0
                                    Surface(
                                        color = if (isDeficit) Color(0xFFFFEBEE) else Color(0xFFF1F8E9),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, if (isDeficit) RedDebit.copy(alpha = 0.5f) else GreenPrimary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            itemName = stock.name
                                            if (stock.salePrice > 0) {
                                                knownUnitPrice = stock.salePrice
                                                val q = itemQty.toDoubleOrNull() ?: 1.0
                                                itemTotal = String.format("%.0f", q * stock.salePrice)
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = if (isDeficit) RedDebit else GreenPrimary
                                            )
                                            Text(
                                                text = if (isDeficit) "${stock.name} (عجز: ${stock.quantity.toInt()})"
                                                else if (stock.salePrice > 0) "${stock.name} (${String.format("%,.0f", stock.salePrice)})"
                                                else stock.name,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDeficit) RedDebit else TextDark
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // الصف الرئيسي المطلوب: [اسم الصنف] [الكمية] [القيمة الإجمالية] في صف واحد
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. اسم الصنف
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("اسم الصنف *", fontSize = 10.sp) },
                                placeholder = { Text("سكر، أرز...", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(4.dp)
                            )

                            // 2. الكمية
                            OutlinedTextField(
                                value = itemQty,
                                onValueChange = { onQuantityChanged(it) },
                                label = { Text("الكمية", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(0.75f),
                                shape = RoundedCornerShape(4.dp)
                            )

                            // 3. القيمة الإجمالية
                            OutlinedTextField(
                                value = itemTotal,
                                onValueChange = { onTotalChanged(it) },
                                label = { Text("الإجمالي (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(4.dp)
                            )

                            // زر الإضافة السريع في نفس الصف
                            Button(
                                onClick = { addItemToInvoice() },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .padding(top = 2.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة الصنف", modifier = Modifier.size(18.dp))
                            }
                        }

                        // سعر الواحدة مستنتج تلقائياً
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (inferredUnitPrice > 0) {
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(3.dp),
                                    border = BorderStroke(0.5.dp, GreenPrimary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "💡 سعر الواحدة المستنتج: ${String.format("%,.1f", inferredUnitPrice).replace(".0", "")} ${currency.symbol} (الإجمالي ÷ الكمية)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "💡 سعر الواحدة يُستنتج تلقائياً (الإجمالي ÷ الكمية)",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // 3. شريط عدد الأصناف وإجمالي الفاتورة
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "أصناف الفاتورة (${items.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextDark
                    )
                    Text(
                        "المجموع: ${String.format("%,.0f", subtotal)} ${currency.symbol}",
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            // 4. جدول الأصناف المضافة (مضغوط وعالي الاستغلال للمساحة)
            if (items.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لم تتم إضافة أي صنف بعد. اكتب اسم الصنف والقيمة الإجمالية ثم اضغط (+)",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(items) { index, itm ->
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // الترتيب واسم الصنف
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    "${index + 1}. ${itm.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${itm.quantity} × ${String.format("%,.1f", itm.unitPrice).replace(".0", "")} (سعر الواحدة)",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            // الإجمالي
                            Text(
                                text = "${String.format("%,.0f", itm.total)} ${currency.symbol}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GreenPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            // حذف الصنف
                            IconButton(
                                onClick = { items.removeAt(index) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "حذف الصنف",
                                    tint = RedDebit,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. قسم الإجماليات والخصم والمدفوع والمتبقي (مضغوط جداً)
            item {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // صف الخصم والواصل في صف واحد جنب إلى جنب
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الخصم (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            OutlinedTextField(
                                value = paidStr,
                                onValueChange = { paidStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الواصل / المسدد (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.5.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }

                        // شريط صافي الفاتورة والمتبقي
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("الصافي بعد الخصم:", fontSize = 10.5.sp, color = TextMuted)
                                Text(
                                    "${String.format("%,.0f", netTotal)} ${currency.symbol}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextDark
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (remaining > 0) "المتبقي (دين على العميل):" else "الرصيد خالص:",
                                    fontSize = 10.5.sp,
                                    color = if (remaining > 0) RedDebit else GreenCredit,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${String.format("%,.0f", remaining)} ${currency.symbol}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (remaining > 0) RedDebit else GreenCredit
                                )
                            }
                        }

                        // ملاحظات الفاتورة في سطر واحد مضغوط
                        OutlinedTextField(
                            value = invoiceNote,
                            onValueChange = { invoiceNote = it },
                            label = { Text("ملاحظات الفاتورة أو مرجع السداد (اختياري)", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 11.sp),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }
        }
    }

    // Bluetooth Printer Selection Dialog
    if (showPrinterDialog) {
        BluetoothPrinterPicker(
            onDismiss = { showPrinterDialog = false },
            onDeviceSelected = { device ->
                showPrinterDialog = false
                Toast.makeText(context, "جاري إرسال الفاتورة للطابعة...", Toast.LENGTH_SHORT).show()
                val inv = currentInvoiceObject()
                val bmp = ThermalPrinter.createInvoiceBitmap(customer, inv, is80mm = false)
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
