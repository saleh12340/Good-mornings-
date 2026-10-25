package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

val SoftBackground = Color(0xFFF1F5F9) // Clean slate off-white background
val SoftCardBg = Color(0xFFFFFFFF)
val SoftCardBorder = Color(0xFFE2E8F0)
val SoftInputBg = Color(0xFFF8FAFC)
val SoftShadowLight = Color(0xFFFFFFFF)
val SoftShadowDark = Color(0x1F64748B)

/**
 * Neumorphic Soft Card with smooth borders and gentle elevation
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = SoftCardBg,
    borderColor: Color = SoftCardBorder,
    elevation: Dp = 1.5.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = SoftShadowDark,
                spotColor = SoftShadowDark
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            content = content
        )
    }
}

/**
 * Soft pill button for modern tactile feel
 */
@Composable
fun SoftPillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = GreenPrimary,
    unselectedColor: Color = Color.Transparent,
    selectedTextColor: Color = Color.White,
    unselectedTextColor: Color = TextDark,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) selectedColor else unselectedColor)
            .border(
                1.dp,
                if (isSelected) selectedColor else Color(0xFFCBD5E1),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.invoke()
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedTextColor else unselectedTextColor
            )
        }
    }
}

/**
 * Built-in Quick Calculator Dialog for quick tallying in grocer ledgers
 */
@Composable
fun QuickCalculatorDialog(
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var display by remember { mutableStateOf(initialValue.ifBlank { "0" }) }
    var operator by remember { mutableStateOf<String?>(null) }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }

    fun onDigit(d: String) {
        if (isNewNumber || display == "0") {
            display = d
            isNewNumber = false
        } else {
            display += d
        }
    }

    fun onOp(op: String) {
        val current = display.toDoubleOrNull() ?: 0.0
        if (operand1 == null) {
            operand1 = current
        } else if (operator != null) {
            val res = when (operator) {
                "+" -> operand1!! + current
                "-" -> operand1!! - current
                "×" -> operand1!! * current
                "÷" -> if (current != 0.0) operand1!! / current else 0.0
                else -> current
            }
            operand1 = res
            display = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
        }
        operator = op
        isNewNumber = true
    }

    fun onEqual() {
        val current = display.toDoubleOrNull() ?: 0.0
        val first = operand1
        val op = operator
        if (first != null && op != null) {
            val res = when (op) {
                "+" -> first + current
                "-" -> first - current
                "×" -> first * current
                "÷" -> if (current != 0.0) first / current else 0.0
                else -> current
            }
            display = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
            operand1 = null
            operator = null
            isNewNumber = true
        }
    }

    fun onClear() {
        display = "0"
        operand1 = null
        operator = null
        isNewNumber = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SoftCardBorder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("آلة حاسبة سريعة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    TextButton(onClick = onDismiss) {
                        Text("إغلاق", color = TextMuted, fontSize = 12.sp)
                    }
                }

                // Display Screen (Neumorphic Inset)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftInputBg)
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (operand1 != null && operator != null) {
                            Text(
                                text = "${if (operand1!! % 1.0 == 0.0) operand1!!.toLong() else operand1} $operator",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = display,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary,
                            maxLines = 1
                        )
                    }
                }

                // Keypad grid
                val buttons = listOf(
                    listOf("C", "÷", "×", "⌫"),
                    listOf("7", "8", "9", "-"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "="),
                    listOf("0", "00", ".", "✓")
                )

                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { btn ->
                            val isAction = btn in listOf("+", "-", "×", "÷", "=")
                            val isConfirm = btn == "✓"
                            val isClear = btn == "C" || btn == "⌫"

                            Button(
                                onClick = {
                                    when (btn) {
                                        "C" -> onClear()
                                        "⌫" -> {
                                            if (display.length > 1) display = display.dropLast(1)
                                            else display = "0"
                                        }
                                        "+", "-", "×", "÷" -> onOp(btn)
                                        "=" -> onEqual()
                                        "✓" -> {
                                            onEqual()
                                            val finalVal = display.toDoubleOrNull() ?: 0.0
                                            onConfirm(finalVal)
                                        }
                                        "." -> {
                                            if (!display.contains(".")) {
                                                display += "."
                                                isNewNumber = false
                                            }
                                        }
                                        else -> onDigit(btn)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isConfirm -> GreenPrimary
                                        isAction -> Color(0xFFE8F5E9)
                                        isClear -> Color(0xFFFFEBEE)
                                        else -> Color(0xFFF8FAFC)
                                    },
                                    contentColor = when {
                                        isConfirm -> Color.White
                                        isAction -> GreenPrimary
                                        isClear -> RedDebit
                                        else -> TextDark
                                    }
                                ),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = if (btn == "✓") "إدراج" else btn,
                                    fontSize = if (btn == "✓") 13.sp else 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Intelligent Parser for Arabic Speech Input
 * e.g. "4000 كرتون عصير 2" -> Item: "كرتون عصير", Qty: 2, Total: 4000
 * e.g. "سكر 500" -> Item: "سكر", Qty: 1, Total: 500
 * e.g. "رز 2 3000" -> Item: "رز", Qty: 2, Total: 3000
 */
object SmartVoiceParser {
    data class ParsedItem(
        val name: String,
        val quantity: Double,
        val total: Double
    )

    private fun normalizeDigits(input: String): String {
        val arabicIndic = "٠١٢٣٤٥٦٧٨٩"
        var out = input
        for (i in 0..9) {
            out = out.replace(arabicIndic[i], ('0' + i))
        }
        return out
    }

    fun parse(spokenText: String): ParsedItem {
        val normalized = normalizeDigits(spokenText.trim())
        if (normalized.isBlank()) return ParsedItem("", 1.0, 0.0)

        // Split into tokens
        val tokens = normalized.split("\\s+".toRegex())
        val numbers = mutableListOf<Double>()
        val words = mutableListOf<String>()

        tokens.forEach { token ->
            val cleanNum = token.filter { it.isDigit() || it == '.' }
            val d = cleanNum.toDoubleOrNull()
            if (d != null && cleanNum.isNotEmpty() && (token == cleanNum || token.all { it.isDigit() || it == '.' })) {
                numbers.add(d)
            } else {
                words.add(token)
            }
        }

        val itemName = words.joinToString(" ").trim()

        return when (numbers.size) {
            0 -> ParsedItem(itemName.ifBlank { spokenText.trim() }, 1.0, 0.0)
            1 -> {
                // Single number: likely the total price
                ParsedItem(itemName.ifBlank { "صنف عام" }, 1.0, numbers[0])
            }
            else -> {
                // Two or more numbers: usually larger number is total price, smaller number is quantity
                val sorted = numbers.sorted()
                val qty = sorted.first()
                val total = sorted.last()
                ParsedItem(itemName.ifBlank { "صنف عام" }, if (qty <= 0) 1.0 else qty, total)
            }
        }
    }
}
