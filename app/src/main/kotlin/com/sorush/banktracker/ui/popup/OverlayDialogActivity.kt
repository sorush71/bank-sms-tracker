package com.sorush.banktracker.ui.popup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.model.CategoryNode
import com.sorush.banktracker.model.TransactionItem
import com.sorush.banktracker.model.TransactionType
import com.sorush.banktracker.receiver.SmsReceiver
import java.text.NumberFormat
import java.util.Locale

class OverlayDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val txId = intent.getLongExtra("EXTRA_TX_ID", -1L)
        val amountToman = intent.getLongExtra("EXTRA_AMOUNT_TOMAN", 0L)
        val typeStr = intent.getStringExtra("EXTRA_TYPE") ?: TransactionType.EXPENSE.name
        val type = TransactionType.valueOf(typeStr)
        val bankName = intent.getStringExtra("EXTRA_BANK_NAME") ?: "تراکنش بانکی"
        val rawMessage = intent.getStringExtra("EXTRA_RAW_MESSAGE") ?: ""

        if (amountToman <= 0) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                TransactionPopup(
                    txId = txId,
                    amountToman = amountToman,
                    type = type,
                    bankName = bankName,
                    rawMessage = rawMessage,
                    onDismiss = { finish() },
                    onSave = { category, subCategory, notes, items ->
                        val db = DatabaseHelper.getInstance(applicationContext)
                        if (txId != -1L) {
                            db.updateTransactionCategorization(
                                txId = txId,
                                category = category,
                                subCategory = subCategory,
                                notes = notes,
                                isIgnored = false,
                                items = items
                            )
                            SmsReceiver.cancelNotification(applicationContext, txId.toInt())
                        } else {
                            db.insertTransaction(
                                amountToman = amountToman,
                                type = type,
                                category = category,
                                subCategory = subCategory,
                                bankName = bankName,
                                rawMessage = rawMessage,
                                isIgnored = false,
                                isPending = false,
                                notes = notes,
                                items = items
                            )
                        }
                        Toast.makeText(applicationContext, "تراکنش در دسته «$category» ثبت شد", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onIgnore = {
                        val db = DatabaseHelper.getInstance(applicationContext)
                        if (txId != -1L) {
                            db.updateTransactionCategorization(
                                txId = txId,
                                category = "جابه‌جایی / نادیده گرفته‌شده",
                                subCategory = "",
                                notes = "نادیده گرفته شد",
                                isIgnored = true,
                                items = emptyList()
                            )
                            SmsReceiver.cancelNotification(applicationContext, txId.toInt())
                        } else {
                            db.insertTransaction(
                                amountToman = amountToman,
                                type = type,
                                category = "جابه‌جایی / نادیده گرفته‌شده",
                                bankName = bankName,
                                rawMessage = rawMessage,
                                isIgnored = true,
                                isPending = false
                            )
                        }
                        Toast.makeText(applicationContext, "تراکنش نادیده گرفته شد", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun TransactionPopup(
    txId: Long,
    amountToman: Long,
    type: TransactionType,
    bankName: String,
    rawMessage: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<TransactionItem>) -> Unit,
    onIgnore: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { DatabaseHelper.getInstance(context) }

    // Mode: 0 for Simple Categorization, 1 for Split Purchase (فاکتور چندقلمی)
    var isSplitMode by remember { mutableStateOf(false) }

    var categoryText by remember { mutableStateOf("") }
    var selectedParentCategory by remember { mutableStateOf("خوراک و سوپرمارکت") }
    var notesText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }

    // Split purchase items
    val splitItems = remember { mutableStateListOf<TransactionItem>() }

    // Load Parmis categories tree
    var categoryTree by remember { mutableStateOf(listOf<CategoryNode>()) }

    LaunchedEffect(Unit) {
        categoryTree = db.getCategoryTree(type)
        if (categoryTree.isNotEmpty()) {
            selectedParentCategory = categoryTree.first().name
        }
    }

    LaunchedEffect(categoryText) {
        suggestions = db.getSuggestedCategories(categoryText, limit = 6)
    }

    val formattedAmount = remember(amountToman) {
        NumberFormat.getNumberInstance(Locale.US).format(amountToman)
    }

    val typeColor = if (type == TransactionType.DEPOSIT) Color(0xFF2E7D32) else Color(0xFFC62828)
    val typeBg = if (type == TransactionType.DEPOSIT) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bank Name & Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = type.titleFa,
                            color = typeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = bankName,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Amount
                Text(
                    text = "$formattedAmount تومان",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = typeColor,
                    textAlign = TextAlign.Center
                )

                // Toggle between Simple and Split Invoice
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSplitMode) "تفکیک ریز اقلام فاکتور:" else "این مبلغ بابت چی بود؟",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = {
                        isSplitMode = !isSplitMode
                        if (isSplitMode && splitItems.isEmpty()) {
                            splitItems.add(TransactionItem(title = "سوپرمارکت", amountToman = amountToman, notes = ""))
                        }
                    }) {
                        Text(
                            text = if (isSplitMode) "بازگشت به دسته کلی" else "🛒 تفکیک اقلام فاکتور",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isSplitMode) {
                    // --- Single Categorization Mode ---
                    Spacer(modifier = Modifier.height(6.dp))

                    // Parent Category Selector
                    if (categoryTree.isNotEmpty()) {
                        Text(
                            text = "گروه اصلی (برگرفته از پارمیس):",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categoryTree) { node ->
                                val isSelected = node.name == selectedParentCategory
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedParentCategory = node.name
                                        if (node.subCategories.isNotEmpty()) {
                                            categoryText = node.subCategories.first().name
                                        }
                                    },
                                    label = { Text(node.name, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Input Field (Sub-category or custom tag with autocomplete)
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("زیردسته یا نام خرج (مثلاً: سیگار، نان...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Suggestion Chips (as user types)
                    AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "پیشنهادات سریع:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(suggestions) { tag ->
                                    SuggestionChip(
                                        onClick = { categoryText = tag },
                                        label = { Text(tag, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notes / Description field
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("توضیحات اختیاری (مثلاً: قیمت کیلویی، مکان...)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                } else {
                    // --- Split Purchase / Invoice Mode ---
                    val currentItemsSum = splitItems.sumOf { it.amountToman }
                    val diff = amountToman - currentItemsSum

                    Text(
                        text = "مجموع اقلام: ${NumberFormat.getNumberInstance(Locale.US).format(currentItemsSum)} از $formattedAmount تومان" +
                                if (diff != 0L) " (${if (diff > 0) "باقیمانده: " else "اضافه: "}${NumberFormat.getNumberInstance(Locale.US).format(Math.abs(diff))} ت)" else " ✓",
                        fontSize = 11.sp,
                        color = if (diff == 0L) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(splitItems.indices.toList()) { index ->
                            val item = splitItems[index]
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = item.title,
                                            onValueChange = { newTitle ->
                                                splitItems[index] = item.copy(title = newTitle, subCategory = newTitle)
                                            },
                                            label = { Text("زیربخش / نام کالا (مثلاً: مرغ)", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1.3f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = if (item.amountToman > 0) item.amountToman.toString() else "",
                                            onValueChange = { newAmtStr ->
                                                val amt = newAmtStr.replace(",", "").toLongOrNull() ?: 0L
                                                splitItems[index] = item.copy(amountToman = amt)
                                            },
                                            label = { Text("قیمت (تومان)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1.2f),
                                            singleLine = true
                                        )
                                        IconButton(onClick = { splitItems.removeAt(index) }) {
                                            Text("✕", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = item.notes,
                                        onValueChange = { newNotes ->
                                            splitItems[index] = item.copy(notes = newNotes)
                                        },
                                        label = { Text("توضیحات (مثلاً: قیمت کیلویی، تعداد)", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = {
                                    val remaining = (amountToman - splitItems.sumOf { it.amountToman }).coerceAtLeast(0L)
                                    splitItems.add(TransactionItem(title = "", amountToman = remaining, notes = ""))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ افزودن قلم دیگر به فاکتور", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons: Save & Ignore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onIgnore,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("نادیده گرفتن", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (isSplitMode && splitItems.isNotEmpty()) {
                                onSave(selectedParentCategory, "فاکتور چندقلمی", notesText, splitItems.toList())
                            } else {
                                val finalSubCat = if (categoryText.isBlank()) "سایر" else categoryText.trim()
                                onSave(selectedParentCategory, finalSubCat, notesText, emptyList())
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("ثبت تراکنش", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
