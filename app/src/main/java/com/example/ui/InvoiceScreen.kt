package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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

    // Manual item inputs: اسم الصنف - العدد - سعر الواحدة - الإجمالي
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("1") }
    var itemUnitPrice by remember { mutableStateOf("") }
    var itemTotal by remember { mutableStateOf("") }

    // Voice recognition feedback state
    var voiceRecognizedFeedback by remember { mutableStateOf<String?>(null) }

    fun onQuantityChanged(newQtyStr: String) {
        itemQty = newQtyStr.filter { ch -> ch.isDigit() || ch == '.' }
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val u = itemUnitPrice.toDoubleOrNull()
        if (u != null && u > 0) {
            itemTotal = String.format("%.0f", q * u)
        } else {
            val t = itemTotal.toDoubleOrNull()
            if (t != null && t > 0 && q > 0) {
                itemUnitPrice = String.format("%.1f", t / q).replace(".0", "")
            }
        }
    }

    fun onUnitPriceChanged(newUnitStr: String) {
        itemUnitPrice = newUnitStr.filter { ch -> ch.isDigit() || ch == '.' }
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val u = itemUnitPrice.toDoubleOrNull() ?: 0.0
        if (q > 0 && u > 0) {
            itemTotal = String.format("%.0f", q * u)
        }
    }

    fun onTotalChanged(newTotalStr: String) {
        itemTotal = newTotalStr.filter { ch -> ch.isDigit() || ch == '.' }
        val q = itemQty.toDoubleOrNull() ?: 1.0
        val t = itemTotal.toDoubleOrNull() ?: 0.0
        if (q > 0 && t > 0) {
            itemUnitPrice = String.format("%.1f", t / q).replace(".0", "")
        }
    }

    fun addItemToInvoice(name: String = itemName, qtyStr: String = itemQty, totalStr: String = itemTotal) {
        val q = qtyStr.toDoubleOrNull() ?: 1.0
        val t = totalStr.toDoubleOrNull() ?: 0.0
        if (name.isBlank()) {
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
                name = name.trim(),
                quantity = q,
                unitPrice = unitP,
                total = t
            )
        )
        // Reset inputs
        itemName = ""
        itemQty = "1"
        itemUnitPrice = ""
        itemTotal = ""
        voiceRecognizedFeedback = null
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val parsed = SmartVoiceParser.parse(spoken)
                itemName = parsed.name
                itemQty = if (parsed.quantity % 1.0 == 0.0) parsed.quantity.toLong().toString() else parsed.quantity.toString()
                itemTotal = if (parsed.total % 1.0 == 0.0) parsed.total.toLong().toString() else String.format("%.0f", parsed.total)
                if (parsed.quantity > 0 && parsed.total > 0) {
                    itemUnitPrice = String.format("%.1f", parsed.total / parsed.quantity).replace(".0", "")
                }
                voiceRecognizedFeedback = "تم التعرف: ${parsed.name} (عدد: ${parsed.quantity}، إجمالي: ${parsed.total} ${currency.symbol})"
                Toast.makeText(context, "تم التعرف على الصوت بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "انطق: [القيمة] [الصنف] [الكمية]\nمثال: 4000 كرتون عصير 2")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "التعرف الصوتي غير مدعوم على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
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
    var showUnsavedExitDialog by remember { mutableStateOf(false) }

    // Intercept hardware and gesture back if there are unsaved invoice items
    BackHandler {
        if (items.isNotEmpty()) {
            showUnsavedExitDialog = true
        } else {
            onBack()
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (items.isNotEmpty()) {
                                showUnsavedExitDialog = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
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
                            text = "فاتورة #${invoiceNumber} - بقالة العزي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "العميل: ${customer.name}",
                            fontSize = 11.sp,
                            color = Color(0xFFC8E6C9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Reset Invoice items button
                    IconButton(
                        onClick = {
                            if (items.isNotEmpty()) {
                                items.clear()
                                Toast.makeText(context, "تم مسح أصناف الفاتورة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "إعادة تعيين",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Share Options
                    Box {
                        IconButton(
                            onClick = { showShareMenu = true },
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
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Soft Neumorphic Bottom Action Bar (Screenshot 1)
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Summary Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المبلغ الإجمالي: ${String.format("%,.0f", netTotal)} ${currency.symbol}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextDark
                        )
                        Text(
                            text = if (remaining > 0) "المتبقي: ${String.format("%,.0f", remaining)} ${currency.symbol}" else "خالص ✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (remaining > 0) RedDebit else GreenCredit
                        )
                    }

                    // 3 Large Action Buttons: Save, Thermal Print, Share (Screenshot 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // حفظ (Save)
                        Button(
                            onClick = {
                                if (items.isEmpty()) {
                                    Toast.makeText(context, "الرجاء إضافة صنف واحد على الأقل للفاتورة", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                onSaveInvoice(currentInvoiceObject())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // طباعة حرارية (Thermal Print - Deep Blue)
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 31 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    showPrinterDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("طباعة حرارية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // مشاركة (Share - WhatsApp)
                        OutlinedButton(
                            onClick = {
                                val inv = currentInvoiceObject()
                                val bmp = ThermalPrinter.createInvoiceBitmap(customer, inv, is80mm = false)
                                ShareUtils.shareImage(context, bmp, "inv_${inv.invoiceNumber}", customer.phone, "فاتورة مبيعات - ${customer.name}")
                            },
                            border = BorderStroke(1.dp, Color(0xFF25D366)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("مشاركة", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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
                .background(SoftBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Smart Arabic Voice Input Card (Matching Screenshot 1)
            item {
                SoftCard(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Instruction prompt box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SoftInputBg)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "انطق: [القيمة] [الصنف] [الكمية]",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = TextDark
                                )
                                Text(
                                    text = "(مثال: 4000 كرتون عصير 2)",
                                    fontSize = 10.5.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Dark Blue / Green Soft Mic Button
                        Button(
                            onClick = { launchVoiceInput() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "صوتي", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("صوتي", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Voice recognized banner
                    if (voiceRecognizedFeedback != null) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = voiceRecognizedFeedback!!,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { addItemToInvoice() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("+ إضافة الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Manual Item Entry Strip Card (Screenshot 1)
            item {
                SoftCard(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Stock suggestions chips
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
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, if (isDeficit) RedDebit.copy(alpha = 0.5f) else GreenPrimary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            itemName = stock.name
                                            if (stock.salePrice > 0) {
                                                itemUnitPrice = stock.salePrice.toString()
                                                val q = itemQty.toDoubleOrNull() ?: 1.0
                                                itemTotal = String.format("%.0f", q * stock.salePrice)
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (stock.salePrice > 0) "${stock.name} (${stock.salePrice.toInt()})" else stock.name,
                                            fontSize = 10.5.sp,
                                            color = if (isDeficit) RedDebit else TextDark,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Row: [التفاصيل (الصنف)] [العدد] [سعر الواحدة] [الإجمالي]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. التفاصيل (الصنف)
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("التفاصيل (الصنف) *", fontSize = 10.sp) },
                                placeholder = { Text("أرز، سكر...", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp),
                                modifier = Modifier.weight(1.5f).height(54.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    unfocusedContainerColor = SoftInputBg
                                )
                            )

                            // 2. العدد (الكمية)
                            OutlinedTextField(
                                value = itemQty,
                                onValueChange = { onQuantityChanged(it) },
                                label = { Text("العدد", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 12.sp),
                                modifier = Modifier.weight(0.7f).height(54.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    unfocusedContainerColor = SoftInputBg
                                )
                            )

                            // 3. سعر الواحدة
                            OutlinedTextField(
                                value = itemUnitPrice,
                                onValueChange = { onUnitPriceChanged(it) },
                                label = { Text("سعر الواحدة", fontSize = 10.sp) },
                                placeholder = { Text("مستنتج", fontSize = 9.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 12.sp),
                                modifier = Modifier.weight(0.9f).height(54.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    unfocusedContainerColor = SoftInputBg
                                )
                            )

                            // 4. الإجمالي
                            OutlinedTextField(
                                value = itemTotal,
                                onValueChange = { onTotalChanged(it) },
                                label = { Text("الإجمالي (${currency.symbol})", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    unfocusedContainerColor = SoftInputBg
                                )
                            )
                        }

                        // Wide "+ إضافة الصنف إلى الفاتورة" Button (Screenshot 1)
                        Button(
                            onClick = { addItemToInvoice() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إضافة الصنف إلى الفاتورة", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }

            // 3. The Physical Bill Card (Authentic receipt styling from Screenshot 1)
            item {
                SoftCard(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(14.dp),
                    borderColor = Color(0xFFCBD5E1)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Bill Header Row: بقالة العزي ج: 776425052 | [✓] نقداً [ ] آجل | رقم | التاريخ
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("بقالة العزي", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                                    Text("ج: 776425052", fontSize = 10.sp, color = TextMuted)
                                }

                                // Payment Type Toggle: نقداً / آجل
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clickable { paymentType = PaymentType.CASH }
                                            .background(if (paymentType == PaymentType.CASH) GreenPrimary else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "نقداً",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (paymentType == PaymentType.CASH) Color.White else TextDark
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clickable { paymentType = PaymentType.CREDIT }
                                            .background(if (paymentType == PaymentType.CREDIT) RedDebit else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "آجل",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (paymentType == PaymentType.CREDIT) Color.White else TextDark
                                        )
                                    }
                                }

                                // Currency Toggle: ر.ي / ر.س
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clickable { currency = AppCurrency.YER }
                                            .background(if (currency == AppCurrency.YER) GreenPrimary else Color.Transparent)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("ر.ي", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (currency == AppCurrency.YER) Color.White else TextDark)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clickable { currency = AppCurrency.SAR }
                                            .background(if (currency == AppCurrency.SAR) AmberSAR else Color.Transparent)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("ر.س", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (currency == AppCurrency.SAR) Color.White else TextDark)
                                    }
                                }

                                // Invoice No and Date
                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(
                                        color = Color(0xFFFFEBEE),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, RedDebit)
                                    ) {
                                        Text(
                                            "رقم: $invoiceNumber",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RedDebit,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(invoiceDate.split(" ").firstOrNull() ?: invoiceDate, fontSize = 9.5.sp, color = TextMuted)
                                }
                            }
                        }

                        // Customer Line and Quick Customer Chips
                        OutlinedTextField(
                            value = customer.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العميل", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFCBD5E1),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                unfocusedContainerColor = SoftInputBg
                            )
                        )

                        // Table Header (م | التفاصيل | العدد | سعر الواحدة | الإجمالي)
                        Surface(
                            color = Color(0xFF1E3A8A),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("م", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f), textAlign = TextAlign.Center)
                                Text("التفاصيل", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("العدد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                                Text("سعر الواحدة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                                Text("الإجمالي", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("حذف", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f), textAlign = TextAlign.Center)
                            }
                        }

                        // Items list inside the Bill
                        if (items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "الفاتورة فارغة. أضف الأصناف عبر الصوت 🎤 أو الشريط أعلاه",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        } else {
                            items.forEachIndexed { index, itm ->
                                Surface(
                                    color = if (index % 2 == 0) Color.White else Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(0.3f), textAlign = TextAlign.Center)
                                        Text(itm.name, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = TextDark, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${itm.quantity}", fontSize = 11.sp, color = TextDark, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                                        Text(String.format("%,.0f", itm.unitPrice), fontSize = 11.sp, color = TextDark, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                                        Text(String.format("%,.0f", itm.total), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)

                                        // Delete icon
                                        IconButton(
                                            onClick = { items.removeAt(index) },
                                            modifier = Modifier.weight(0.4f).size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "حذف",
                                                tint = RedDebit,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Grand Total Banner (Deep Blue banner from Screenshot 1)
                        Surface(
                            color = Color(0xFF1E3A8A),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "المبلغ الإجمالي العام (Grand Total):",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${String.format("%,.0f", netTotal)} ${currency.symbol}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Payment Details Row (المبلغ الواصل + كامل + المتبقي)
                        Surface(
                            color = SoftInputBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("المبلغ الواصل:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                    OutlinedTextField(
                                        value = paidStr,
                                        onValueChange = { paidStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                        placeholder = { Text("0.0", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.width(80.dp).height(46.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GreenPrimary,
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            unfocusedContainerColor = Color.White
                                        )
                                    )

                                    // Button: كامل (Quick full payment)
                                    Button(
                                        onClick = { paidStr = netTotal.toInt().toString() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("كامل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                                    }
                                }

                                // Remaining indicator (خالص ✓ vs متبقي)
                                Surface(
                                    color = if (remaining > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, if (remaining > 0) RedDebit else GreenPrimary)
                                ) {
                                    Text(
                                        text = if (remaining > 0) "المتبقي: ${String.format("%,.0f", remaining)} ${currency.symbol}" else "المتبقي: خالص ✓",
                                        color = if (remaining > 0) RedDebit else GreenPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Optional Note / Discount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("الخصم (${currency.symbol})", fontSize = 10.sp) },
                                placeholder = { Text("0", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(0.8f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = invoiceNote,
                                onValueChange = { invoiceNote = it },
                                label = { Text("ملاحظة الفاتورة", fontSize = 10.sp) },
                                placeholder = { Text("اختياري", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1.2f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Store Footer Notice (Screenshot 1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ت.المشتري: ......", fontSize = 9.sp, color = TextMuted)
                            Text("البضاعة المباعة لا ترد ولا تستبدل", fontSize = 9.sp, color = TextMuted)
                            Text("ت.البائع: ......", fontSize = 9.sp, color = TextMuted)
                        }
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
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Unsaved Exit Confirmation Dialog
    if (showUnsavedExitDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedExitDialog = false },
            title = {
                Text("تأكيد الخروج", fontWeight = FontWeight.Bold, color = RedDebit)
            },
            text = {
                Text("يوجد أصناف مضافة في الفاتورة لم يتم حفظها بعد.\nهل أنت متأكد من الخروج دون حفظ؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedExitDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("خروج دون حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedExitDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
