package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.StoreRepository
import com.example.model.Customer
import com.example.model.StockItem
import com.example.model.getCurrentDateTime
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreDialog(
    customers: List<Customer>,
    stockList: List<StockItem>,
    onDismiss: () -> Unit,
    onRestore: (List<Customer>, List<StockItem>) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Backup, 1: Restore
    var pasteText by remember { mutableStateOf("") }
    var parsedBackup by remember { mutableStateOf<Pair<List<Customer>?, List<StockItem>?>?>(null) }
    var restoreMergeMode by remember { mutableStateOf(false) } // false = Replace, true = Merge

    val totalEntries = remember(customers) { customers.sumOf { it.entries.size } }

    // Save File Picker (Create Document)
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val backupJson = StoreRepository.exportBackup(customers, stockList)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(backupJson.toByteArray())
                }
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح في ملف", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل حفظ الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Open File Picker (Open Document)
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!jsonString.isNullOrBlank()) {
                    val result = StoreRepository.importBackup(jsonString)
                    if (result.first != null || result.second != null) {
                        parsedBackup = result
                        Toast.makeText(context, "تم فحص ملف النسخة بنجاح. راجع التفاصيل أدناه للاستعادة", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "الملف المحدد لا يحتوي على بيانات نسخة احتياطية صالحة", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(26.dp))
                Text("النسخ الاحتياطي والاستعادة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 0) GreenPrimary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "نسخ احتياطي (تصدير)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedTab == 0) Color.White else TextDark
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 1) GreenPrimary else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "استعادة البيانات (استيراد)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedTab == 1) Color.White else TextDark
                        )
                    }
                }

                if (selectedTab == 0) {
                    // TAB 0: BACKUP
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = GreenLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("إحصائيات البيانات الحالية:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenPrimary)
                            Text("• عدد العملاء: ${customers.size} عميل", fontSize = 12.sp, color = TextDark)
                            Text("• إجمالي العمليات والفواتير: $totalEntries حركة", fontSize = 12.sp, color = TextDark)
                            Text("• عدد الأصناف بالمخزن: ${stockList.size} صنف", fontSize = 12.sp, color = TextDark)
                            Text("• تاريخ اليوم: ${getCurrentDateTime()}", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    // Export / Share via WhatsApp or other apps
                    Button(
                        onClick = {
                            try {
                                val backupText = StoreRepository.exportBackup(customers, stockList)
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                val fileName = "alizzi_backup_$dateStr.json"
                                val cacheDir = File(context.cacheDir, "shared_cache").apply { mkdirs() }
                                val backupFile = File(cacheDir, fileName)
                                FileOutputStream(backupFile).use { it.write(backupText.toByteArray()) }

                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)

                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية بقالة العزي - $dateStr")
                                    putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية لقاعدة بيانات بقالة العزي بتاريخ ${getCurrentDateTime()}\nتضم ${customers.size} عميل و $totalEntries حركة و ${stockList.size} صنف.")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة النسخة الاحتياطية"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل المشاركة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("مشاركة أو إرسال النسخة (واتساب / درايف / إيميل)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Save as file to Downloads / Storage
                    OutlinedButton(
                        onClick = {
                            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            saveFileLauncher.launch("alizzi_backup_$dateStr.json")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("حفظ كملف في وحدة تخزين الهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    }

                    // Copy raw JSON to clipboard
                    OutlinedButton(
                        onClick = {
                            val backupText = StoreRepository.exportBackup(customers, stockList)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Alizzi Backup", backupText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ نص النسخة الاحتياطية إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextDark)
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ بيانات النسخة كنص للحافظة", fontSize = 12.sp, color = TextDark)
                    }
                } else {
                    // TAB 1: RESTORE
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                            Text(
                                "تنبيه: استعادة النسخة الاحتياطية تسمح لك باسترجاع كافة حسابات العملاء وديونهم وأصناف المخزن.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Method 1: Pick JSON File from device
                    Button(
                        onClick = {
                            openFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار ملف نسخة احتياطية من الهاتف (JSON)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Method 2: Paste JSON Text
                    Text("أو لصق نص النسخة الاحتياطية مباشرة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = {
                            pasteText = it
                            if (it.isNotBlank()) {
                                val result = StoreRepository.importBackup(it)
                                if (result.first != null || result.second != null) {
                                    parsedBackup = result
                                }
                            }
                        },
                        placeholder = { Text("الصق هنا النص المنسوخ من النسخة الاحتياطية...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Parsed Backup Review
                    if (parsedBackup != null) {
                        val importedCust = parsedBackup!!.first ?: emptyList()
                        val importedStock = parsedBackup!!.second ?: emptyList()
                        val importedEntriesCount = importedCust.sumOf { it.entries.size }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تم التحقق من بيانات النسخة بنجاح!", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenPrimary)
                                }
                                Text("• ${importedCust.size} عميل مسترجع", fontSize = 12.sp, color = TextDark)
                                Text("• $importedEntriesCount حركة وفاتورة مسترجعة", fontSize = 12.sp, color = TextDark)
                                Text("• ${importedStock.size} صنف مخزن مسترجع", fontSize = 12.sp, color = TextDark)

                                Spacer(Modifier.height(4.dp))
                                Text("طريقة الاستعادة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !restoreMergeMode,
                                        onClick = { restoreMergeMode = false },
                                        label = { Text("استبدال البيانات الحالية", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = restoreMergeMode,
                                        onClick = { restoreMergeMode = true },
                                        label = { Text("دمج مع البيانات الحالية", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val finalCustomers: List<Customer> = if (restoreMergeMode) {
                                            // Merge by customer name / id
                                            val currentList = customers.toMutableList()
                                            for (newC in importedCust) {
                                                val existingIndex = currentList.indexOfFirst {
                                                    it.id == newC.id || it.name.trim().equals(newC.name.trim(), ignoreCase = true)
                                                }
                                                if (existingIndex >= 0) {
                                                    val existing = currentList[existingIndex]
                                                    val mergedEntries = (existing.entries + newC.entries).distinctBy { it.id }
                                                    currentList[existingIndex] = existing.copy(entries = mergedEntries)
                                                } else {
                                                    currentList.add(newC)
                                                }
                                            }
                                            currentList
                                        } else {
                                            importedCust
                                        }

                                        val finalStock: List<StockItem> = if (restoreMergeMode) {
                                            val currentStock = stockList.toMutableList()
                                            for (newS in importedStock) {
                                                val existingIndex = currentStock.indexOfFirst {
                                                    it.id == newS.id || it.name.trim().equals(newS.name.trim(), ignoreCase = true)
                                                }
                                                if (existingIndex < 0) {
                                                    currentStock.add(newS)
                                                }
                                            }
                                            currentStock
                                        } else {
                                            importedStock
                                        }

                                        onRestore(finalCustomers, finalStock)
                                        Toast.makeText(context, "تمت استعادة البيانات بنجاح تام!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تأكيد استعادة البيانات الآن", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
