package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StoreRepository
import com.example.model.*
import com.example.ui.CustomerAccountScreen
import com.example.ui.GlobalTransactionsScreen
import com.example.ui.InventoryScreen
import com.example.ui.InvoiceScreen
import com.example.ui.theme.*
import java.util.UUID

sealed class Screen {
    object Main : Screen()
    data class CustomerAccount(val customerId: String) : Screen()
    data class InvoiceBuilder(val customerId: String, val invoiceEntry: TransactionEntry?) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BackgroundGray
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var customers by remember { mutableStateOf(StoreRepository.loadCustomers(context)) }
    var stockList by remember { mutableStateOf(StoreRepository.loadStock(context)) }
    var selectedMainTab by remember { mutableStateOf(0) }

    fun updateCustomers(newList: List<Customer>) {
        val sorted = StoreRepository.sortCustomersNewestFirst(newList)
        customers = sorted
        StoreRepository.saveCustomers(context, sorted)
    }

    fun updateStock(newList: List<StockItem>) {
        val sorted = StoreRepository.sortStockNewestFirst(newList)
        stockList = sorted
        StoreRepository.saveStock(context, sorted)
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    when (val scr = currentScreen) {
        is Screen.Main -> {
            Scaffold(
                bottomBar = {
                    val deficitCount = remember(stockList) { stockList.count { it.quantity < 0 } }
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = selectedMainTab == 0,
                            onClick = { selectedMainTab = 0 },
                            icon = { Icon(Icons.Default.People, contentDescription = "العملاء والديون") },
                            label = { Text("العملاء", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = GreenLight
                            )
                        )
                        NavigationBarItem(
                            selected = selectedMainTab == 1,
                            onClick = { selectedMainTab = 1 },
                            icon = {
                                BadgedBox(badge = {
                                    if (deficitCount > 0) {
                                        Badge(containerColor = RedDebit) {
                                            Text("$deficitCount", color = Color.White)
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Inventory2, contentDescription = "المخزن والمخزون")
                                }
                            },
                            label = { Text("المخزن", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = GreenLight
                            )
                        )
                        NavigationBarItem(
                            selected = selectedMainTab == 2,
                            onClick = { selectedMainTab = 2 },
                            icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "الفواتير والعمليات") },
                            label = { Text("الفواتير", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = GreenLight
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedMainTab) {
                        0 -> MainCustomerListScreen(
                            customers = customers,
                            stockList = stockList,
                            onSelectCustomer = { customerId ->
                                currentScreen = Screen.CustomerAccount(customerId)
                            },
                            onUpdateCustomers = { updateCustomers(it) },
                            onQuickInvoice = { customerId ->
                                currentScreen = Screen.InvoiceBuilder(customerId, null)
                            }
                        )
                        1 -> InventoryScreen(
                            stockList = stockList,
                            onUpdateStock = { updateStock(it) },
                            onBack = null
                        )
                        2 -> GlobalTransactionsScreen(
                            customers = customers,
                            onSelectCustomer = { customerId ->
                                currentScreen = Screen.CustomerAccount(customerId)
                            },
                            onOpenInvoice = { customerId, invoice ->
                                currentScreen = Screen.InvoiceBuilder(customerId, invoice)
                            }
                        )
                    }
                }
            }
        }
        is Screen.CustomerAccount -> {
            val customer = customers.find { it.id == scr.customerId }
            if (customer == null) {
                currentScreen = Screen.Main
            } else {
                BackHandler { currentScreen = Screen.Main }
                CustomerAccountScreen(
                    customer = customer,
                    onBack = { currentScreen = Screen.Main },
                    onUpdateCustomer = { updated ->
                        updateCustomers(customers.map { if (it.id == updated.id) updated else it })
                    },
                    onOpenInvoiceBuilder = { invoiceToEdit ->
                        currentScreen = Screen.InvoiceBuilder(customer.id, invoiceToEdit)
                    }
                )
            }
        }
        is Screen.InvoiceBuilder -> {
            val customer = customers.find { it.id == scr.customerId }
            if (customer == null) {
                currentScreen = Screen.Main
            } else {
                BackHandler { currentScreen = Screen.CustomerAccount(customer.id) }
                InvoiceScreen(
                    customer = customer,
                    existingInvoice = scr.invoiceEntry,
                    stockList = stockList,
                    onBack = { currentScreen = Screen.CustomerAccount(customer.id) },
                    onSaveInvoice = { savedInvoice ->
                        val updatedEntries = if (scr.invoiceEntry == null) {
                            customer.entries + savedInvoice
                        } else {
                            customer.entries.map { if (it.id == scr.invoiceEntry.id) savedInvoice else it }
                        }
                        val updatedCustomer = customer.copy(entries = updatedEntries)
                        updateCustomers(customers.map { if (it.id == updatedCustomer.id) updatedCustomer else it })

                        // Deduct sold items from inventory, creating negative quantities for deficit or missing items
                        val updatedStock = StoreRepository.processInvoiceInventory(context, savedInvoice.items, savedInvoice.currency)
                        updateStock(updatedStock)

                        currentScreen = Screen.CustomerAccount(customer.id)
                        Toast.makeText(context, "تم حفظ الفاتورة وتحديث المخزن والرصيد", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCustomerListScreen(
    customers: List<Customer>,
    stockList: List<StockItem> = emptyList(),
    onSelectCustomer: (String) -> Unit,
    onUpdateCustomers: (List<Customer>) -> Unit,
    onQuickInvoice: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }
    var showQuickInvoicePicker by remember { mutableStateOf(false) }

    // Summary calculation
    val (totalYER, totalSAR) = remember(customers) {
        var yer = 0.0
        var sar = 0.0
        customers.forEach { c ->
            val (bY, bS) = StoreRepository.calculateCustomerBalances(c)
            if (bY > 0) yer += bY
            if (bS > 0) sar += bS
        }
        Pair(yer, sar)
    }

    val sortedCustomers = remember(customers) {
        StoreRepository.sortCustomersNewestFirst(customers)
    }

    val filteredCustomers = sortedCustomers.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "بقالة العزي للمواد الغذائية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val backupText = StoreRepository.exportBackup(customers, stockList)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية - بقالة العزي:\n$backupText")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "تصدير النسخة الاحتياطية"))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = "نسخ احتياطي",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    customerToEdit = null
                    showAddCustomerDialog = true
                },
                containerColor = GreenPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundGray)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary Card
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
                        Text("إجمالي الديون المعلقة (يمني)", fontSize = 10.sp, color = GreenSecondary)
                        Text(String.format("%,.0f ر.ي", totalYER), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RedDebit)
                    }

                    VerticalDivider(modifier = Modifier.height(34.dp), color = Color(0xFFA5D6A7))

                    Column {
                        Text("إجمالي الديون (سعودي)", fontSize = 10.sp, color = GreenSecondary)
                        Text(String.format("%,.0f ر.س", totalSAR), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RedDebit)
                    }

                    VerticalDivider(modifier = Modifier.height(34.dp), color = Color(0xFFA5D6A7))

                    Column(horizontalAlignment = Alignment.End) {
                        Text("العملاء", fontSize = 10.sp, color = GreenSecondary)
                        Text("${customers.size}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GreenPrimary)
                    }
                }
            }

            // بطاقة إضافة عميل سريع في أعلى الشاشة (مربعات الإدخال في صفوف أفقية)
            var quickCustomerName by remember { mutableStateOf("") }
            var quickCustomerPhone by remember { mutableStateOf("") }
            var quickCustomerAddress by remember { mutableStateOf("") }
            var quickCustomerNote by remember { mutableStateOf("") }
            var showExtraCustomerInputs by remember { mutableStateOf(false) }

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
                    // الصف الأول: [اسم العميل] [الهاتف] [+ إضافة]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickCustomerName,
                            onValueChange = { quickCustomerName = it },
                            label = { Text("اسم العميل *", fontSize = 10.sp) },
                            placeholder = { Text("محمد، علي...", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 11.5.sp),
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        OutlinedTextField(
                            value = quickCustomerPhone,
                            onValueChange = { quickCustomerPhone = it },
                            label = { Text("رقم الهاتف", fontSize = 10.sp) },
                            placeholder = { Text("77xxxxxxx", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = TextStyle(fontSize = 11.5.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Button(
                            onClick = {
                                val cleanName = quickCustomerName.trim()
                                if (cleanName.isBlank()) {
                                    Toast.makeText(context, "الرجاء كتابة اسم العميل", Toast.LENGTH_SHORT).show()
                                } else if (StoreRepository.isCustomerDuplicate(customers, cleanName, null)) {
                                    Toast.makeText(context, "تنبيه: يوجد عميل مسجل بهذا الاسم مسبقاً!", Toast.LENGTH_LONG).show()
                                } else {
                                    val finalNote = if (quickCustomerAddress.isNotBlank()) {
                                        if (quickCustomerNote.isNotBlank()) "${quickCustomerAddress.trim()} | ${quickCustomerNote.trim()}" else quickCustomerAddress.trim()
                                    } else quickCustomerNote.trim()

                                    val newCustomer = Customer(
                                        name = cleanName,
                                        phone = quickCustomerPhone.trim(),
                                        note = finalNote
                                    )
                                    onUpdateCustomers(customers + newCustomer)
                                    quickCustomerName = ""
                                    quickCustomerPhone = ""
                                    quickCustomerAddress = ""
                                    quickCustomerNote = ""
                                    showExtraCustomerInputs = false
                                    Toast.makeText(context, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("إضافة", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // الصف الثاني: [العنوان / المنطقة] [ملاحظات] [زر إظهار/إخفاء]
                    if (showExtraCustomerInputs) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickCustomerAddress,
                                onValueChange = { quickCustomerAddress = it },
                                label = { Text("العنوان / المنطقة", fontSize = 10.sp) },
                                placeholder = { Text("اختياري", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )

                            OutlinedTextField(
                                value = quickCustomerNote,
                                onValueChange = { quickCustomerNote = it },
                                label = { Text("ملاحظات إضافية", fontSize = 10.sp) },
                                placeholder = { Text("اختياري", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExtraCustomerInputs = !showExtraCustomerInputs }
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (showExtraCustomerInputs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GreenSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (showExtraCustomerInputs) "إخفاء حقول العنوان والملاحظات" else "إضافة العنوان أو ملاحظات للعميل",
                            fontSize = 10.sp,
                            color = GreenSecondary
                        )
                    }
                }
            }

            // Search Bar & Fast Invoice Button في صف واحد
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن عميل بالاسم أو الهاتف...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = GreenPrimary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = { showQuickInvoicePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberSAR),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("فاتورة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Customer List
            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PeopleOutline, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لم يتم تسجيل أي عملاء بعد",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        Button(
                            onClick = {
                                customerToEdit = null
                                showAddCustomerDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text("إضافة أول عميل")
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
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val (bY, bS) = StoreRepository.calculateCustomerBalances(customer)
                        MainCustomerCard(
                            customer = customer,
                            balanceYER = bY,
                            balanceSAR = bS,
                            onClick = { onSelectCustomer(customer.id) },
                            onEdit = {
                                customerToEdit = customer
                                showAddCustomerDialog = true
                            },
                            onDelete = {
                                if (customer.entries.isNotEmpty()) {
                                    customerToDelete = customer
                                } else {
                                    onUpdateCustomers(customers.filter { it.id != customer.id })
                                    Toast.makeText(context, "تم حذف العميل", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Customer Dialog
    if (showAddCustomerDialog) {
        var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
        var phone by remember { mutableStateOf(customerToEdit?.phone ?: "") }
        var address by remember { mutableStateOf("") }
        var note by remember { mutableStateOf(customerToEdit?.note ?: "") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text(if (customerToEdit == null) "إضافة عميل جديد" else "تعديل بيانات العميل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // الصف الأول (جنب إلى جنب): اسم العميل + رقم الهاتف
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم العميل *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("رقم الهاتف") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف الثاني (جنب إلى جنب): العنوان / المنطقة + ملاحظات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("العنوان / المنطقة") },
                            placeholder = { Text("اختياري") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("ملاحظات") },
                            placeholder = { Text("اختياري") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "الرجاء إدخال اسم العميل", Toast.LENGTH_SHORT).show()
                    } else if (StoreRepository.isCustomerDuplicate(customers, name, customerToEdit?.id)) {
                        Toast.makeText(context, "تنبيه: يوجد عميل مسجل بهذا الاسم مسبقاً لمنع تكرار الحسابات!", Toast.LENGTH_LONG).show()
                    } else {
                        val finalNote = if (address.isNotBlank()) {
                            if (note.isNotBlank()) "${address.trim()} | ${note.trim()}" else address.trim()
                        } else note.trim()

                        if (customerToEdit == null) {
                            val newC = Customer(name = name.trim(), phone = phone.trim(), note = finalNote)
                            onUpdateCustomers(customers + newC)
                            Toast.makeText(context, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show()
                        } else {
                            val updated = customerToEdit!!.copy(name = name.trim(), phone = phone.trim(), note = finalNote)
                            onUpdateCustomers(customers.map { if (it.id == updated.id) updated else it })
                            Toast.makeText(context, "تم تحديث بيانات العميل", Toast.LENGTH_SHORT).show()
                        }
                        showAddCustomerDialog = false
                    }
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete with Warning
    if (customerToDelete != null) {
        val c = customerToDelete!!
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("تأكيد حذف العميل") },
            text = { Text("العميل (${c.name}) لديه (${c.entries.size}) عمليات وفواتير مسجلة. هل تريد حذفه مع كافة معاملاته نهائياً؟") },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCustomers(customers.filter { it.id != c.id })
                    customerToDelete = null
                    Toast.makeText(context, "تم حذف العميل وكافة معاملاته", Toast.LENGTH_SHORT).show()
                }) {
                    Text("تأكيد الحذف", color = RedDebit)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Quick Invoice Customer Picker
    if (showQuickInvoicePicker) {
        AlertDialog(
            onDismissRequest = { showQuickInvoicePicker = false },
            title = { Text("اختر العميل لإنشاء الفاتورة") },
            text = {
                if (customers.isEmpty()) {
                    Text("لا يوجد عملاء. أضف عميلاً أولاً.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(customers) { c ->
                            TextButton(
                                onClick = {
                                    showQuickInvoicePicker = false
                                    onQuickInvoice(c.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    if (c.phone.isNotBlank()) Text(c.phone, color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickInvoicePicker = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun MainCustomerCard(
    customer: Customer,
    balanceYER: Double,
    balanceSAR: Double,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                    .background(GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.name.take(1),
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                if (customer.phone.isNotBlank()) {
                    Text(customer.phone, fontSize = 11.sp, color = TextMuted)
                }
            }

            // Balances Column
            Column(horizontalAlignment = Alignment.End) {
                if (balanceYER != 0.0 || balanceSAR == 0.0) {
                    Text(
                        text = String.format("%,.0f ر.ي", balanceYER),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (balanceYER > 0) RedDebit else if (balanceYER < 0) GreenCredit else TextMuted
                    )
                }
                if (balanceSAR != 0.0) {
                    Text(
                        text = String.format("%,.0f ر.س", balanceSAR),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (balanceSAR > 0) RedDebit else if (balanceSAR < 0) GreenCredit else TextMuted
                    )
                }
                Text(
                    text = if (balanceYER > 0 || balanceSAR > 0) "عليه مديونية" else if (balanceYER < 0 || balanceSAR < 0) "له رصيد" else "خالص",
                    fontSize = 10.sp,
                    color = if (balanceYER > 0 || balanceSAR > 0) RedDebit else if (balanceYER < 0 || balanceSAR < 0) GreenCredit else TextMuted
                )
            }

            Spacer(Modifier.width(4.dp))

            if (customer.phone.isNotBlank()) {
                IconButton(
                    onClick = {
                        val statement = StoreRepository.generateAccountStatementText(customer)
                        com.example.util.ShareUtils.shareTextToWhatsApp(context, customer.phone, statement)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال كشف الحساب واتساب", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                }
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
