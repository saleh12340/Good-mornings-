package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StoreRepository
import com.example.model.AppCurrency
import com.example.model.StockItem
import com.example.model.getCurrentDateTime
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    stockList: List<StockItem>,
    onUpdateStock: (List<StockItem>) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<StockItem?>(null) }
    var itemToDelete by remember { mutableStateOf<StockItem?>(null) }
    var itemToRestock by remember { mutableStateOf<StockItem?>(null) }

    val sortedStock = remember(stockList) {
        StoreRepository.sortStockNewestFirst(stockList)
    }

    val filteredStock = sortedStock.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    val negativeCount = stockList.count { it.quantity < 0 }
    val totalItems = stockList.size
    val totalInventoryValue = stockList.filter { it.quantity > 0 }.sumOf { it.quantity * it.salePrice }

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
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onBack != null) {
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
                        }
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "مخزن بقالة العزي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            itemToEdit = null
                            showAddDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "إضافة صنف",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    itemToEdit = null
                    showAddDialog = true
                },
                containerColor = GreenPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = "إضافة صنف جديد للمخزن")
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
            // Inventory Summary Card
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
                        Text("إجمالي الأصناف", fontSize = 10.sp, color = GreenSecondary)
                        Text("$totalItems صنف", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GreenPrimary)
                    }

                    VerticalDivider(modifier = Modifier.height(34.dp), color = Color(0xFFA5D6A7))

                    Column {
                        Text("أصناف سالبة (عجز)", fontSize = 10.sp, color = GreenSecondary)
                        Text(
                            text = "$negativeCount صنف",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (negativeCount > 0) RedDebit else GreenPrimary
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(34.dp), color = Color(0xFFA5D6A7))

                    Column(horizontalAlignment = Alignment.End) {
                        Text("قيمة المخزون (بيع)", fontSize = 10.sp, color = GreenSecondary)
                        Text(String.format("%,.0f ر.ي", totalInventoryValue), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
                    }
                }
            }

            // بطاقة إضافة صنف للمخزن في أعلى الشاشة (مربعات الإدخال في صفوف أفقية)
            var quickItemName by remember { mutableStateOf("") }
            var quickItemQty by remember { mutableStateOf("10") }
            var quickItemSalePrice by remember { mutableStateOf("") }
            var quickItemCostPrice by remember { mutableStateOf("") }
            var quickItemUnit by remember { mutableStateOf("حبة") }
            var quickItemCurrency by remember { mutableStateOf(AppCurrency.YER) }
            var showExtraStockInputs by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // الصف الأول: [اسم الصنف] [الكمية] [سعر البيع] [+ إضافة]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickItemName,
                            onValueChange = { quickItemName = it },
                            label = { Text("اسم الصنف *", fontSize = 10.sp) },
                            placeholder = { Text("سكر، زيت...", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 11.5.sp),
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        OutlinedTextField(
                            value = quickItemQty,
                            onValueChange = { quickItemQty = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                            label = { Text("الكمية", fontSize = 10.sp) },
                            placeholder = { Text("10", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontSize = 11.5.sp),
                            modifier = Modifier.weight(0.75f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        OutlinedTextField(
                            value = quickItemSalePrice,
                            onValueChange = { quickItemSalePrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("سعر البيع *", fontSize = 10.sp) },
                            placeholder = { Text("0", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontSize = 11.5.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Button(
                            onClick = {
                                val cleanName = quickItemName.trim()
                                val saleP = quickItemSalePrice.toDoubleOrNull() ?: 0.0
                                val qty = quickItemQty.toDoubleOrNull() ?: 0.0
                                val costP = quickItemCostPrice.toDoubleOrNull() ?: 0.0

                                if (cleanName.isBlank()) {
                                    Toast.makeText(context, "الرجاء كتابة اسم الصنف", Toast.LENGTH_SHORT).show()
                                } else if (StoreRepository.isStockItemDuplicate(stockList, cleanName, null)) {
                                    Toast.makeText(context, "تنبيه: يوجد صنف مسجل بنفس الاسم في المخزن!", Toast.LENGTH_LONG).show()
                                } else {
                                    val newItem = StockItem(
                                        name = cleanName,
                                        quantity = qty,
                                        unit = if (quickItemUnit.isNotBlank()) quickItemUnit.trim() else "حبة",
                                        salePrice = saleP,
                                        costPrice = costP,
                                        currency = quickItemCurrency,
                                        updatedAt = getCurrentDateTime()
                                    )
                                    onUpdateStock(stockList + newItem)
                                    quickItemName = ""
                                    quickItemQty = "10"
                                    quickItemSalePrice = ""
                                    quickItemCostPrice = ""
                                    showExtraStockInputs = false
                                    Toast.makeText(context, "تمت إضافة الصنف للمخزن بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("إضافة", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // الصف الثاني: [سعر التكلفة] [الوحدة] [العملة] [زر إظهار/إخفاء]
                    if (showExtraStockInputs) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickItemCostPrice,
                                onValueChange = { quickItemCostPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("سعر التكلفة (شراء)", fontSize = 10.sp) },
                                placeholder = { Text("اختياري", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(6.dp)
                            )

                            OutlinedTextField(
                                value = quickItemUnit,
                                onValueChange = { quickItemUnit = it },
                                label = { Text("الوحدة", fontSize = 10.sp) },
                                placeholder = { Text("حبة/كرتون", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BackgroundGray)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable { quickItemCurrency = AppCurrency.YER }
                                        .background(if (quickItemCurrency == AppCurrency.YER) GreenPrimary else Color.Transparent, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("ر.ي", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (quickItemCurrency == AppCurrency.YER) Color.White else TextDark)
                                }
                                Box(
                                    modifier = Modifier
                                        .clickable { quickItemCurrency = AppCurrency.SAR }
                                        .background(if (quickItemCurrency == AppCurrency.SAR) AmberSAR else Color.Transparent, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("ر.س", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (quickItemCurrency == AppCurrency.SAR) Color.White else TextDark)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExtraStockInputs = !showExtraStockInputs }
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (showExtraStockInputs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GreenSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (showExtraStockInputs) "إخفاء خيارات التكلفة والوحدة" else "إضافة سعر التكلفة والوحدة والعملة",
                            fontSize = 10.sp,
                            color = GreenSecondary
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث عن صنف في المخزن...", fontSize = 12.sp) },
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

            // Stock List (Newest First)
            if (filteredStock.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                        Text("لا توجد أصناف في المخزن مطابقة للبحث", fontSize = 13.sp, color = TextMuted)
                        Button(
                            onClick = {
                                itemToEdit = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text("إضافة صنف جديد")
                        }
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
                    items(filteredStock, key = { it.id }) { item ->
                        StockItemCard(
                            item = item,
                            onRestock = { itemToRestock = item },
                            onEdit = {
                                itemToEdit = item
                                showAddDialog = true
                            },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Stock Item Dialog (مربعات إدخال في صفوف جنب إلى جنب + منع التكرار)
    if (showAddDialog) {
        var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
        var qtyStr by remember { mutableStateOf(itemToEdit?.quantity?.toString() ?: "10") }
        var unit by remember { mutableStateOf(itemToEdit?.unit ?: "حبة") }
        var salePriceStr by remember { mutableStateOf(itemToEdit?.salePrice?.toInt()?.toString() ?: "") }
        var costPriceStr by remember { mutableStateOf(itemToEdit?.costPrice?.toInt()?.toString() ?: "") }
        var currency by remember { mutableStateOf(itemToEdit?.currency ?: AppCurrency.YER) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (itemToEdit == null) "إضافة صنف للمخزن" else "تعديل الصنف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // الصف الأول (جنب إلى جنب): اسم الصنف + الوحدة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم الصنف *") },
                            placeholder = { Text("سكر، زيت...") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("الوحدة") },
                            placeholder = { Text("حبة/كيس/كرتون") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف الثاني (جنب إلى جنب): الكمية + سعر البيع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = qtyStr,
                            onValueChange = { qtyStr = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                            label = { Text("الكمية") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = { salePriceStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("سعر البيع *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    // الصف الثالث (جنب إلى جنب): سعر الشراء (التكلفة) + العملة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = costPriceStr,
                            onValueChange = { costPriceStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("سعر التكلفة (شراء)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )

                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = currency == AppCurrency.YER,
                                onClick = { currency = AppCurrency.YER },
                                label = { Text("ر.ي", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = currency == AppCurrency.SAR,
                                onClick = { currency = AppCurrency.SAR },
                                label = { Text("ر.س", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isBlank()) {
                        Toast.makeText(context, "الرجاء كتابة اسم الصنف", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }

                    // Duplicate Prevention
                    if (StoreRepository.isStockItemDuplicate(stockList, cleanName, itemToEdit?.id)) {
                        Toast.makeText(context, "تنبيه: يوجد صنف مسجل بنفس الاسم في المخزن!", Toast.LENGTH_LONG).show()
                        return@TextButton
                    }

                    val q = qtyStr.toDoubleOrNull() ?: 0.0
                    val sPrice = salePriceStr.toDoubleOrNull() ?: 0.0
                    val cPrice = costPriceStr.toDoubleOrNull() ?: (sPrice * 0.85)

                    if (itemToEdit == null) {
                        val newItem = StockItem(
                            name = cleanName,
                            quantity = q,
                            unit = unit.ifBlank { "حبة" },
                            salePrice = sPrice,
                            costPrice = cPrice,
                            currency = currency,
                            updatedAt = getCurrentDateTime()
                        )
                        val updated = listOf(newItem) + stockList
                        onUpdateStock(StoreRepository.sortStockNewestFirst(updated))
                        Toast.makeText(context, "تمت إضافة الصنف للمخزن", Toast.LENGTH_SHORT).show()
                    } else {
                        val updated = itemToEdit!!.copy(
                            name = cleanName,
                            quantity = q,
                            unit = unit.ifBlank { "حبة" },
                            salePrice = sPrice,
                            costPrice = cPrice,
                            currency = currency,
                            updatedAt = getCurrentDateTime()
                        )
                        val newList = stockList.map { if (it.id == updated.id) updated else it }
                        onUpdateStock(StoreRepository.sortStockNewestFirst(newList))
                        Toast.makeText(context, "تم تحديث الصنف", Toast.LENGTH_SHORT).show()
                    }
                    showAddDialog = false
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Quick Restock Dialog (تزويد المخزون)
    if (itemToRestock != null) {
        val itm = itemToRestock!!
        var addQtyStr by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { itemToRestock = null },
            title = { Text("تزويد مخزون (${itm.name})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الرصيد الحالي: ${itm.quantity} ${itm.unit}", fontWeight = FontWeight.Bold, color = if (itm.quantity < 0) RedDebit else GreenPrimary)
                    OutlinedTextField(
                        value = addQtyStr,
                        onValueChange = { addQtyStr = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                        label = { Text("الكمية المضافة") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val addQty = addQtyStr.toDoubleOrNull() ?: 0.0
                    val updated = itm.copy(quantity = itm.quantity + addQty, updatedAt = getCurrentDateTime())
                    val newList = stockList.map { if (it.id == updated.id) updated else it }
                    onUpdateStock(StoreRepository.sortStockNewestFirst(newList))
                    itemToRestock = null
                    Toast.makeText(context, "تم تحديث كمية المخزون", Toast.LENGTH_SHORT).show()
                }) {
                    Text("تأكيد الإضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRestock = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Stock Confirmation Dialog
    if (itemToDelete != null) {
        val itm = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("حذف صنف من المخزن") },
            text = { Text("هل أنت متأكد من حذف الصنف (${itm.name}) من المخزن نهائياً؟") },
            confirmButton = {
                TextButton(onClick = {
                    val newList = stockList.filter { it.id != itm.id }
                    onUpdateStock(newList)
                    itemToDelete = null
                    Toast.makeText(context, "تم حذف الصنف من المخزن", Toast.LENGTH_SHORT).show()
                }) {
                    Text("حذف", color = RedDebit)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun StockItemCard(
    item: StockItem,
    onRestock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isNegative = item.quantity < 0
    val isZero = item.quantity == 0.0
    val badgeColor = if (isNegative) RedDebit else if (isZero) Color(0xFFE65100) else GreenCredit
    val badgeText = if (isNegative) "عجز بالسالب: ${item.quantity} ${item.unit}" else if (isZero) "منتهي: 0 ${item.unit}" else "متوفر: ${item.quantity} ${item.unit}"

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isNegative) Icons.Default.Warning else Icons.Default.Storefront,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                    Text("سعر البيع: ${String.format("%,.0f", item.salePrice)} ${item.currency.symbol}", fontSize = 11.sp, color = TextMuted)
                }
            }

            // Quick restock button (+)
            IconButton(onClick = onRestock, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "تزويد", tint = GreenPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedDebit, modifier = Modifier.size(16.dp))
            }
        }
    }
}
