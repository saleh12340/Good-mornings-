package com.alazzi.grocery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

private val Green = Color(0xFF218653)
private val Page = Color(0xFFF5F7F5)
private val Ink = Color(0xFF202720)
private val SPP = "alazzi_store_v1"
private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class Entry(val id: String, val title: String, val amount: Double, val kind: String, val date: String, val note: String = "")
data class Customer(val id: String, val name: String, val phone: String, val note: String, val entries: List<Entry> = emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245,247,245)
        window.navigationBarColor = android.graphics.Color.rgb(245,247,245)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Page)) { StoreApp() } }
    }
}

private fun dateNow() = SimpleDateFormat("yyyy/MM/dd  hh:mm a", Locale.getDefault()).format(Date())
private fun cash(n: Double) = String.format(Locale.US, "%,.0f ر.ي", n)
private fun balance(c: Customer) = c.entries.sumOf { if (it.kind == "invoice" || it.kind == "debt") it.amount else -it.amount }

private fun loadCustomers(activity: ComponentActivity): List<Customer> {
    return try {
        val raw = activity.getSharedPreferences(SPP, 0).getString("customers", "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val ea = o.optJSONArray("entries") ?: JSONArray()
            val es = (0 until ea.length()).map { j -> val e=ea.getJSONObject(j); Entry(e.optString("id"),e.optString("title"),e.optDouble("amount"),e.optString("kind"),e.optString("date"),e.optString("note")) }
            Customer(o.optString("id"),o.optString("name"),o.optString("phone"),o.optString("note"),es)
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveCustomers(activity: ComponentActivity, customers: List<Customer>) {
    val arr = JSONArray()
    customers.forEach { c ->
        val o=JSONObject().put("id",c.id).put("name",c.name).put("phone",c.phone).put("note",c.note)
        val ea=JSONArray(); c.entries.forEach { e -> ea.put(JSONObject().put("id",e.id).put("title",e.title).put("amount",e.amount).put("kind",e.kind).put("date",e.date).put("note",e.note)) }
        o.put("entries",ea); arr.put(o)
    }
    activity.getSharedPreferences(SPP,0).edit().putString("customers",arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreApp() {
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current
    var customers by remember { mutableStateOf(loadCustomers(activity)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showCustomer by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerNote by remember { mutableStateOf("") }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryKind by remember { mutableStateOf("invoice") }
    var entryTitle by remember { mutableStateOf("") }
    var entryAmount by remember { mutableStateOf("") }
    var entryNote by remember { mutableStateOf("") }
    var editEntryId by remember { mutableStateOf<String?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pendingPrint by remember { mutableStateOf("") }
    val selected = customers.firstOrNull { it.id == selectedId }
    fun persist(next: List<Customer>) { customers = next; saveCustomers(activity,next) }
    fun share(text: String) { val i=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) }; context.startActivity(Intent.createChooser(i,"مشاركة")) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { showPrinterDialog = true } else Toast.makeText(context,"يلزم السماح بالبلوتوث للطباعة",Toast.LENGTH_LONG).show()
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            TopAppBar(
                title = { Column(verticalArrangement=Arrangement.spacedBy(0.dp)) { Text(if (showCustomer && selected != null) selected.name else "بقالة العزي", fontSize=18.sp, fontWeight=FontWeight.Bold); Text(if (showCustomer) "حساب العميل" else "العملاء والفواتير", fontSize=11.sp, color=MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { if (showCustomer) IconButton(onClick={showCustomer=false; selectedId=null}) { Icon(Icons.Default.ArrowBack,"رجوع") } else Icon(Icons.Default.Store,null,Modifier.padding(horizontal=12.dp),tint=Green) },
                actions = { if (showCustomer && selected != null) IconButton(onClick={share(customerStatement(selected))}) { Icon(Icons.Default.Share,"مشاركة الحساب") } },
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Page)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick={ if (showCustomer && selected != null) { entryKind="invoice"; entryTitle=""; entryAmount=""; entryNote=""; editEntryId=null; showEntryDialog=true } else { customerName="";customerPhone="";customerNote="";showCustomerDialog=true } },containerColor=Green,contentColor=Color.White) { Icon(Icons.Default.Add,if(showCustomer) "إضافة فاتورة أو عملية" else "إضافة عميل") }
        }
    ) { padding ->
        if (!showCustomer || selected == null) {
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=12.dp, vertical=8.dp)) {
                Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Green),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column { Text("إجمالي أرصدة العملاء",color=Color.White,fontSize=12.sp); Text(cash(customers.sumOf { balance(it) }),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold) }
                        Column(horizontalAlignment=Alignment.End) { Text("عدد العملاء",color=Color.White,fontSize=12.sp); Text(customers.size.toString(),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("العملاء",fontSize=17.sp,fontWeight=FontWeight.Bold,color=Ink)
                Spacer(Modifier.height(6.dp))
                if (customers.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.Store,null,tint=Green,modifier=Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text("لا يوجد عملاء بعد"); Text("اضغط + لإضافة أول عميل",fontSize=12.sp,color=Color.Gray) } }
                else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=84.dp)) {
                    items(customers,key={it.id}) { c ->
                        Card(modifier=Modifier.fillMaxWidth().clickable { selectedId=c.id;showCustomer=true },shape=RoundedCornerShape(13.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Text(c.name,fontWeight=FontWeight.SemiBold,fontSize=15.sp); if(c.phone.isNotBlank()) Text(c.phone,fontSize=11.sp,color=Color.Gray); Text("${c.entries.size} عملية",fontSize=11.sp,color=Color.Gray) }
                                Column(horizontalAlignment=Alignment.End) { Text(cash(balance(c)),fontWeight=FontWeight.Bold,color=if(balance(c)>0) Green else Ink,fontSize=14.sp); Text(if(balance(c)>0) "عليه" else if(balance(c)<0) "له" else "مصفّى",fontSize=10.sp,color=Color.Gray) }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)) {
                Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Green)) { Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("الرصيد الحالي",color=Color.White,fontSize=12.sp); Text(cash(balance(selected)),color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold); if(selected.phone.isNotBlank()) Text("الهاتف: ${selected.phone}",color=Color.White,fontSize=12.sp); if(selected.note.isNotBlank()) Text(selected.note,color=Color.White,fontSize=11.sp) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()) {
                    Button(onClick={entryKind="invoice";entryTitle="";entryAmount="";entryNote="";editEntryId=null;showEntryDialog=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp,vertical=9.dp)) { Icon(Icons.Default.ReceiptLong,null,Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("فاتورة جديدة",fontSize=12.sp) }
                    OutlinedButton(onClick={entryKind="payment";entryTitle="سند قبض";entryAmount="";entryNote="";editEntryId=null;showEntryDialog=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=9.dp)) { Text("تسجيل دفعة",fontSize=12.sp) }
                    OutlinedButton(onClick={entryKind="debt";entryTitle="دين / عملية";entryAmount="";entryNote="";editEntryId=null;showEntryDialog=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=9.dp)) { Text("عملية أخرى",fontSize=12.sp) }
                }
                Spacer(Modifier.height(12.dp)); Text("الفواتير والعمليات",fontSize=16.sp,fontWeight=FontWeight.Bold)
                if(selected.entries.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("لا توجد عمليات مسجلة لهذا العميل",color=Color.Gray,fontSize=13.sp) }
                else LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=88.dp)) {
                    items(selected.entries.sortedByDescending { it.date },key={it.id}) { e ->
                        Card(shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(e.title,fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text(e.date,fontSize=10.sp,color=Color.Gray) }; Text((if(e.kind=="payment") "−" else "+")+cash(e.amount),fontWeight=FontWeight.Bold,color=if(e.kind=="payment") Green else Ink,fontSize=14.sp) }
                                if(e.note.isNotBlank()) Text(e.note,fontSize=12.sp,color=Color.Gray)
                                Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()) {
                                    TextButton(onClick={entryKind=e.kind;entryTitle=e.title;entryAmount=e.amount.toLong().toString();entryNote=e.note;editEntryId=e.id;showEntryDialog=true}) { Icon(Icons.Default.Edit,null,Modifier.size(16.dp)); Text("تعديل",fontSize=11.sp) }
                                    TextButton(onClick={ persist(customers.map { c -> if(c.id==selected.id)c.copy(entries=c.entries.filterNot { it.id==e.id }) else c }) }) { Icon(Icons.Default.Delete,null,Modifier.size(16.dp)); Text("حذف",fontSize=11.sp) }
                                    TextButton(onClick={share(entryReceipt(selected,e))}) { Icon(Icons.Default.Share,null,Modifier.size(16.dp)); Text("مشاركة",fontSize=11.sp) }
                                    TextButton(onClick={pendingPrint=entryReceipt(selected,e); if(Build.VERSION.SDK_INT>=31 && ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) else showPrinterDialog=true}) { Icon(Icons.Default.Print,null,Modifier.size(16.dp)); Text("طباعة",fontSize=11.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if(showCustomerDialog) AlertDialog(onDismissRequest={showCustomerDialog=false},title={Text("إضافة عميل")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(customerName,{customerName=it},label={Text("اسم العميل *")},singleLine=true); OutlinedTextField(customerPhone,{customerPhone=it},label={Text("رقم الهاتف")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone)); OutlinedTextField(customerNote,{customerNote=it},label={Text("ملاحظات")},minLines=2) }},confirmButton={TextButton(onClick={ if(customerName.isBlank()) Toast.makeText(context,"أدخل اسم العميل",Toast.LENGTH_SHORT).show() else { val c=Customer(UUID.randomUUID().toString(),customerName.trim(),customerPhone.trim(),customerNote.trim());persist(customers+c);showCustomerDialog=false;selectedId=c.id;showCustomer=true } }) { Text("حفظ") }},dismissButton={TextButton(onClick={showCustomerDialog=false}) { Text("إلغاء") }})

    if(showEntryDialog && selected != null) AlertDialog(onDismissRequest={showEntryDialog=false},title={Text(if(editEntryId==null) "إضافة ${if(entryKind=="invoice") "فاتورة" else "عملية"}" else "تعديل العملية")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("invoice" to "فاتورة","payment" to "قبض","debt" to "دين").forEach { (k,label) -> FilterChip(selected=entryKind==k,onClick={entryKind=k;if(entryTitle.isBlank())entryTitle=label},label={Text(label,fontSize=11.sp)}) } }
        OutlinedTextField(entryTitle,{entryTitle=it},label={Text("العنوان")},singleLine=true)
        OutlinedTextField(entryAmount,{entryAmount=it.filter { ch -> ch.isDigit() || ch=='.' }},label={Text("المبلغ بالريال اليمني *")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        OutlinedTextField(entryNote,{entryNote=it},label={Text("تفاصيل / أصناف الفاتورة")},minLines=2)
    }},confirmButton={TextButton(onClick={val amount=entryAmount.toDoubleOrNull();if(amount==null||amount<=0)Toast.makeText(context,"أدخل مبلغًا صحيحًا",Toast.LENGTH_SHORT).show() else {
        val newEntry=Entry(editEntryId?:UUID.randomUUID().toString(),entryTitle.ifBlank { if(entryKind=="invoice")"فاتورة بيع" else if(entryKind=="payment")"سند قبض" else "عملية دين" },amount,entryKind,if(editEntryId==null)dateNow() else selected.entries.firstOrNull { it.id==editEntryId }?.date?:dateNow(),entryNote)
        persist(customers.map { c -> if(c.id==selected.id) c.copy(entries=if(editEntryId==null)c.entries+newEntry else c.entries.map { if(it.id==editEntryId)newEntry else it }) else c });showEntryDialog=false
    }}) { Text("حفظ") }},dismissButton={TextButton(onClick={showEntryDialog=false}) { Text("إلغاء") }})

    if(showPrinterDialog) {
        val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
        val paired = remember(showPrinterDialog) { try { adapter?.bondedDevices?.toList() ?: emptyList() } catch (_: SecurityException) { emptyList() } }
        AlertDialog(onDismissRequest={showPrinterDialog=false},title={Text("اختيار طابعة Bluetooth")},text={if(adapter==null) Text("هذا الجهاز لا يدعم Bluetooth.") else if(paired.isEmpty()) Text("لا توجد أجهزة مقترنة. افتح إعدادات Bluetooth واقرن الطابعة أولًا.") else LazyColumn { items(paired) { device -> TextButton(onClick={showPrinterDialog=false; printBluetooth(device,pendingPrint) { msg -> activity.runOnUiThread { Toast.makeText(context,msg,Toast.LENGTH_LONG).show() } } },modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(device.name ?: "طابعة Bluetooth"); Text(device.address,fontSize=10.sp,color=Color.Gray) } } } }},confirmButton={TextButton(onClick={showPrinterDialog=false}) { Text("إغلاق") }})
    }
}

private fun customerStatement(c: Customer): String = buildString { appendLine("بقالة العزي للمواد الغذائية");appendLine("كشف حساب العميل: ${c.name}");if(c.phone.isNotBlank())appendLine("الهاتف: ${c.phone}");appendLine("------------------------------");c.entries.sortedBy { it.date }.forEach { appendLine("${it.date} | ${it.title} | ${cash(it.amount)} ${if(it.kind=="payment")"(قبض)" else ""}") };appendLine("------------------------------");appendLine("الرصيد: ${cash(balance(c))}") }
private fun entryReceipt(c: Customer,e: Entry): String = "بقالة العزي للمواد الغذائية\nالعميل: ${c.name}\n${e.title}\nالتاريخ: ${e.date}\nالمبلغ: ${cash(e.amount)}\n${e.note}\n------------------------------\nالرصيد: ${cash(balance(c))}\nشكرًا لتسوقكم معنا\n\n\n"

@SuppressLint("MissingPermission")
private fun printBluetooth(device: BluetoothDevice, receipt: String, done: (String)->Unit) {
    Executors.newSingleThreadExecutor().execute {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket.connect()
            val out=socket.outputStream
            out.write(byteArrayOf(0x1B,0x40))
            out.write(receipt.toByteArray(Charsets.UTF_8))
            out.write(byteArrayOf(0x0A,0x0A,0x0A,0x1D,0x56,0x00))
            out.flush();done("تم إرسال الإيصال للطابعة. إذا ظهرت العربية بشكل غير صحيح فقد تحتاج الطابعة إلى ترميز/صورة ESC-POS.")
        } catch (e: Exception) { done("تعذرت الطباعة: ${e.localizedMessage ?: "تحقق من اقتران الطابعة ودعم ESC/POS"}") }
        finally { try { socket?.close() } catch (_: Exception) {} }
    }
}
