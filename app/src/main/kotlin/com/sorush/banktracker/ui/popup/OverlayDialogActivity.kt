package com.sorush.banktracker.ui.popup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.model.TransactionType
import java.text.NumberFormat
import java.util.Locale

class OverlayDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    amountToman = amountToman,
                    type = type,
                    bankName = bankName,
                    rawMessage = rawMessage,
                    onDismiss = { finish() },
                    onSave = { category ->
                        val db = DatabaseHelper.getInstance(applicationContext)
                        db.insertTransaction(
                            amountToman = amountToman,
                            type = type,
                            category = category,
                            bankName = bankName,
                            rawMessage = rawMessage,
                            isIgnored = false
                        )
                        Toast.makeText(applicationContext, "تراکنش در دسته «$category» ثبت شد", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onIgnore = {
                        val db = DatabaseHelper.getInstance(applicationContext)
                        db.insertTransaction(
                            amountToman = amountToman,
                            type = type,
                            category = "جابه‌جایی / نادیده گرفته‌شده",
                            bankName = bankName,
                            rawMessage = rawMessage,
                            isIgnored = true
                        )
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
    amountToman: Long,
    type: TransactionType,
    bankName: String,
    rawMessage: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onIgnore: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { DatabaseHelper.getInstance(context) }
    var categoryText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }

    // Update suggestions when user types
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
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = "$formattedAmount تومان",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = typeColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "این مبلغ بابت چی بود؟",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category input
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("دسته‌بندی (مثلاً: سیگار، قسط...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (categoryText.isNotBlank()) onSave(categoryText.trim())
                    }),
                    shape = RoundedCornerShape(12.dp)
                )

                // Suggestion chips
                AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "پیشنهادات سریع:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestions) { tag ->
                                SuggestionChip(
                                    onClick = { categoryText = tag },
                                    label = { Text(tag, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        Text("نادیده گرفتن", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val finalCat = if (categoryText.isBlank()) "سایر" else categoryText.trim()
                            onSave(finalCat)
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
