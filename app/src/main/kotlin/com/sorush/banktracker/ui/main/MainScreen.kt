package com.sorush.banktracker.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sorush.banktracker.BuildConfig
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.model.CategoryExpense
import com.sorush.banktracker.model.CategoryNode
import com.sorush.banktracker.model.MonthlySummary
import com.sorush.banktracker.model.Transaction
import com.sorush.banktracker.model.TransactionItem
import com.sorush.banktracker.model.TransactionType
import com.sorush.banktracker.updater.AppUpdater
import com.sorush.banktracker.updater.UpdateInfo
import com.sorush.banktracker.util.JalaliCalendar
import com.sorush.banktracker.util.JalaliDate
import com.sorush.banktracker.util.XiaomiHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val db = remember { DatabaseHelper.getInstance(context) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: گزارش ماهانه, 1: تراکنش‌ها, 2: تنظیمات و بانک‌ها

    // Permissions check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermission = results[Manifest.permission.RECEIVE_SMS] == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مدیریت مالی خودکار",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("داشبورد") },
                    icon = { Text("📊") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("اقساط و بدهی") },
                    icon = { Text("📅") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("تراکنش‌ها") },
                    icon = { Text("💳") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("تنظیمات") },
                    icon = { Text("⚙️") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner if missing
            if (!hasSmsPermission || !hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "⚠️ برای باز شدن خودکار پاپ‌آپ، دسترسی‌های زیر لازم است:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasSmsPermission) {
                                Button(
                                    onClick = {
                                        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        permissionLauncher.launch(perms.toTypedArray())
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                                ) {
                                    Text("مجوز پیامک", fontSize = 12.sp)
                                }
                            }
                            if (!hasOverlayPermission) {
                                Button(
                                    onClick = { XiaomiHelper.openOverlaySettings(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                                ) {
                                    Text("مجوز پاپ‌آپ (Overlay)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> MonthlyDashboardTab(db = db)
                1 -> InstallmentsTab(db = db)
                2 -> TransactionsHistoryTab(db = db)
                3 -> SettingsTab(db = db, onRefreshPermissions = {
                    hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                })
            }
        }
    }
}

@Composable
fun MonthlyDashboardTab(db: DatabaseHelper) {
    val context = LocalContext.current
    val now = remember { JalaliCalendar.now() }
    var currentYear by remember { mutableIntStateOf(now.year) }
    var currentMonth by remember { mutableIntStateOf(now.month) }

    var summary by remember { mutableStateOf<MonthlySummary?>(null) }
    var pendingTransactions by remember { mutableStateOf(listOf<Transaction>()) }

    fun refresh() {
        summary = db.getMonthlySummary(currentYear, currentMonth)
        pendingTransactions = db.getPendingTransactions()
    }

    LaunchedEffect(currentYear, currentMonth) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pending Transactions Card
        if (pendingTransactions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔔 ${pendingTransactions.size} تراکنش در انتظار تعیین دسته‌بندی",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "این تراکنش‌ها ذخیره شدند تا سر فرصت دسته‌بندی یا تفکیک اقلام شوند:",
                            fontSize = 11.sp,
                            color = Color(0xFF6D4C41)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        pendingTransactions.forEach { pTx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${pTx.bankName} (${if (pTx.type == TransactionType.DEPOSIT) "واریز" else "برداشت"})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${NumberFormat.getNumberInstance(Locale.US).format(pTx.amountToman)} تومان",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (pTx.type == TransactionType.DEPOSIT) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                val intent = android.content.Intent(context, com.sorush.banktracker.ui.popup.OverlayDialogActivity::class.java).apply {
                                                    putExtra("EXTRA_TX_ID", pTx.id)
                                                    putExtra("EXTRA_AMOUNT_TOMAN", pTx.amountToman)
                                                    putExtra("EXTRA_TYPE", pTx.type.name)
                                                    putExtra("EXTRA_BANK_NAME", pTx.bankName)
                                                    putExtra("EXTRA_RAW_MESSAGE", pTx.rawMessage)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("تعیین دسته", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                db.updateTransactionCategorization(
                                                    pTx.id,
                                                    "نادیده گرفته شده",
                                                    "",
                                                    "نادیده گرفته شد",
                                                    true,
                                                    emptyList()
                                                )
                                                com.sorush.banktracker.receiver.SmsReceiver.cancelNotification(context, pTx.id.toInt())
                                                refresh()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text("نادیده", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Month Switcher Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentMonth < 12) currentMonth++ else { currentMonth = 1; currentYear++ }
                    }) {
                        Text("▶", fontSize = 16.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${JalaliCalendar.MONTH_NAMES.getOrElse(currentMonth - 1) { "" }} $currentYear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "گزارش مالی ماهانه",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        if (currentMonth > 1) currentMonth-- else { currentMonth = 12; currentYear-- }
                    }) {
                        Text("◀", fontSize = 16.sp)
                    }
                }
            }
        }

        // Summary Cards: Deposit & Expense & Balance
        item {
            summary?.let { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Deposit
                    FinanceCard(
                        title = "کل واریزی‌ها",
                        amount = s.totalDepositToman,
                        color = Color(0xFF2E7D32),
                        bgColor = Color(0xFFE8F5E9),
                        modifier = Modifier.weight(1f)
                    )

                    // Total Expense
                    FinanceCard(
                        title = "کل مخارج",
                        amount = s.totalExpenseToman,
                        color = Color(0xFFC62828),
                        bgColor = Color(0xFFFFEBEE),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Balance Card
        item {
            summary?.let { s ->
                val isPositive = s.balanceToman >= 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPositive) Color(0xFFF1F8E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تراز مالی ماه:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${NumberFormat.getNumberInstance(Locale.US).format(s.balanceToman)} تومان",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = if (isPositive) Color(0xFF33691E) else Color(0xFFB71C1C)
                        )
                    }
                }
            }
        }

        // Category Breakdown Header
        item {
            Text(
                text = "تفکیک مخارج بر اساس دسته‌بندی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Category Breakdown Items
        summary?.let { s ->
            if (s.categoryBreakdown.isEmpty()) {
                item {
                    Text(
                        text = "هیچ هزینه‌ای در این ماه ثبت نشده است.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(s.categoryBreakdown) { cat ->
                    CategoryProgressItem(cat)
                }
            }
        }
    }
}

@Composable
fun FinanceCard(
    title: String,
    amount: Long,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = title, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${NumberFormat.getNumberInstance(Locale.US).format(amount)} تومان",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CategoryProgressItem(item: CategoryExpense) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "${NumberFormat.getNumberInstance(Locale.US).format(item.totalToman)} تومان (${String.format(Locale.US, "%.1f", item.percentage)}%)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun InstallmentsTab(db: DatabaseHelper) {
    val context = LocalContext.current
    val now = remember { JalaliCalendar.now() }
    var installments by remember { mutableStateOf(listOf<com.sorush.banktracker.model.Installment>()) }
    var summary by remember { mutableStateOf<com.sorush.banktracker.model.InstallmentSummary?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        installments = db.getAllInstallments()
        summary = db.getInstallmentSummary()
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Cards: Monthly Total & Remaining Debt
        item {
            summary?.let { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FinanceCard(
                        title = "مجموع اقساط ماه",
                        amount = s.totalMonthlyAmountToman,
                        color = Color(0xFFC62828),
                        bgColor = Color(0xFFFFEBEE),
                        modifier = Modifier.weight(1f)
                    )
                    FinanceCard(
                        title = "کل بدهی باقیمانده",
                        amount = s.totalRemainingDebtToman,
                        color = Color(0xFFE65100),
                        bgColor = Color(0xFFFFF3E0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bank Breakdown Section
        item {
            summary?.let { s ->
                if (s.bankBreakdown.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "مجموع اقساط به تفکیک بانک:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            s.bankBreakdown.forEach { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${b.bankName} (${b.count} فقره):",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${NumberFormat.getNumberInstance(Locale.US).format(b.monthlyTotalToman)} تومان/ماه",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Test Reminder Notification & Add Installment Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("+ ثبت قسط جدید", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        com.sorush.banktracker.receiver.InstallmentReminderReceiver.checkAndTriggerReminders(context)
                        Toast.makeText(context, "بررسی و ارسال نوتیفیکیشن انجام شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔔 تست اعلان", fontSize = 12.sp)
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "لیست اقساط و بدهی‌های فعال",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Installment Cards
        if (installments.isEmpty()) {
            item {
                Text(
                    text = "هیچ قسط فعالی ثبت نشده است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(installments) { inst ->
                InstallmentCard(
                    installment = inst,
                    todayDay = now.day,
                    onMarkPaid = {
                        db.incrementInstallmentPaid(inst.id)
                        refresh()
                    },
                    onDelete = {
                        db.deleteInstallment(inst.id)
                        refresh()
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddInstallmentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, bank, amount, dueDay, months ->
                db.insertInstallment(
                    title = title,
                    bankName = bank,
                    amountToman = amount,
                    dueDay = dueDay,
                    totalMonths = months
                )
                showAddDialog = false
                refresh()
            }
        )
    }
}

@Composable
fun InstallmentCard(
    installment: com.sorush.banktracker.model.Installment,
    todayDay: Int,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val isDueTomorrow = (installment.dueDay == todayDay + 1) || ((todayDay >= 29) && installment.dueDay == 1)
    val daysUntilDue = if (installment.dueDay >= todayDay) installment.dueDay - todayDay else (30 - todayDay + installment.dueDay)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDueTomorrow) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDueTomorrow) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Due Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = installment.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = installment.bankName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isDueTomorrow) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE65100))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("⚠️ فردا سررسید است!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("سررسید: ${installment.dueDay} هر ماه ($daysUntilDue روز مانده)", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "مبلغ قسط ماهانه:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${NumberFormat.getNumberInstance(Locale.US).format(installment.amountToman)} تومان",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color(0xFFC62828)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress: Paid vs Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${installment.paidMonths} از ${installment.totalMonths} قسط پرداخت شده",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "باقیمانده: ${NumberFormat.getNumberInstance(Locale.US).format(installment.totalRemainingDebtToman)} تومان",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { installment.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Buttons: Mark 1 Paid & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMarkPaid,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("✓ پرداخت این ماه", fontSize = 11.sp)
                }

                IconButton(onClick = onDelete) {
                    Text("🗑️", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AddInstallmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, Int, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("بانک قرض‌الحسنه رسالت") }
    var amountStr by remember { mutableStateOf("") }
    var dueDayStr by remember { mutableStateOf("15") }
    var monthsStr by remember { mutableStateOf("24") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت قسط یا وام جدید", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان وام/قسط (مثلاً: وام مسکن)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text("بانک مقصد (حسابی که باید پول توش باشه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("مبلغ هر قسط به تومان (مثلاً: 2600000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dueDayStr,
                        onValueChange = { dueDayStr = it },
                        label = { Text("روز سررسید (۱-۳۱)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthsStr,
                        onValueChange = { monthsStr = it },
                        label = { Text("تعداد کل ماه‌ها") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.replace(",", "").toLongOrNull() ?: 0L
                val day = dueDayStr.toIntOrNull() ?: 1
                val months = monthsStr.toIntOrNull() ?: 12
                if (title.isNotBlank() && amt > 0) {
                    onConfirm(title, bank, amt, day, months)
                }
            }) {
                Text("ثبت")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun TransactionsHistoryTab(db: DatabaseHelper) {
    var transactions by remember { mutableStateOf(listOf<Transaction>()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        transactions = db.getAllTransactions(limit = 200)
    }

    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.bankName.contains(searchQuery, ignoreCase = true) ||
            it.amountToman.toString().contains(searchQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("جستجو در دسته‌ها، بانک‌ها یا مبالغ...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "تراکنشی یافت نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { tx ->
                    TransactionRow(tx)
                }
            }
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction) {
    val date = remember(tx.timestamp) {
        val j = JalaliCalendar.fromTimestamp(tx.timestamp)
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        "${j.formatted()} - ${sdf.format(Date(tx.timestamp))}"
    }

    var isExpanded by remember { mutableStateOf(false) }

    val isDeposit = tx.type == TransactionType.DEPOSIT
    val color = if (tx.isIgnored) Color.Gray else if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val sign = if (isDeposit) "+" else "-"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (tx.items.isNotEmpty()) isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val titleDisplay = when {
                        tx.isIgnored -> "نادیده گرفته شده"
                        tx.subCategory.isNotBlank() && tx.subCategory != tx.category -> "${tx.category} > ${tx.subCategory}"
                        else -> tx.category
                    }
                    Text(
                        text = titleDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${tx.bankName} | $date",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tx.notes.isNotBlank()) {
                        Text(
                            text = "📝 ${tx.notes}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sign${NumberFormat.getNumberInstance(Locale.US).format(tx.amountToman)} تومان",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = color
                    )
                    if (tx.items.isNotEmpty()) {
                        Text(
                            text = "🛒 فاکتور (${tx.items.size} قلم) ${if (isExpanded) "▲" else "▼"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Expanded Split Items View
            AnimatedVisibility(visible = isExpanded && tx.items.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ریز اقلام فاکتور:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tx.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "• ${item.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                if (item.notes.isNotBlank()) {
                                    Text(
                                        text = item.notes,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "${NumberFormat.getNumberInstance(Locale.US).format(item.amountToman)} ت",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(db: DatabaseHelper, onRefreshPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var whitelist by remember { mutableStateOf(db.getAllWhitelistedBanks()) }
    var newSenderId by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }

    var updateStatusText by remember { mutableStateOf("نسخه فعلی: ${BuildConfig.VERSION_NAME}") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Test Popup Simulator Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧪 تست و بررسی عملکرد پاپ‌آپ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "بدون نیاز به پیامک بانکی واقعی، می‌توانید پاپ‌آپ را امتحان و مشاهده کنید:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, com.sorush.banktracker.ui.popup.OverlayDialogActivity::class.java).apply {
                                    putExtra("EXTRA_AMOUNT_TOMAN", 2600000L)
                                    putExtra("EXTRA_TYPE", TransactionType.EXPENSE.name)
                                    putExtra("EXTRA_BANK_NAME", "بانک قرض‌الحسنه رسالت")
                                    putExtra("EXTRA_RAW_MESSAGE", "10.14423005.1\n-26,000,000\n08/14 00:23")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تست رسالت (-۲.۶ م)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, com.sorush.banktracker.ui.popup.OverlayDialogActivity::class.java).apply {
                                    putExtra("EXTRA_AMOUNT_TOMAN", 87000L)
                                    putExtra("EXTRA_TYPE", TransactionType.EXPENSE.name)
                                    putExtra("EXTRA_BANK_NAME", "بانک پاسارگاد")
                                    putExtra("EXTRA_RAW_MESSAGE", "777.888.27870097.1\n-870,000\n08/18 02:56")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تست پاسارگاد (-۸۷ ه)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Xiaomi HyperOS Settings Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "تنظیمات اختصاصی شیائومی (HyperOS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "برای اطمینان از بالا آمدن بی‌درنگ پاپ‌آپ هنگام رسیدن پیامک:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { XiaomiHelper.openAutostartSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("۱. فعال‌سازی شروع خودکار (Autostart)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { XiaomiHelper.openBatterySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("۲. مدیریت باتری (No restrictions)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { XiaomiHelper.openPopupInBackgroundSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("۳. نمایش پاپ‌آپ در پس‌زمینه (Xiaomi Pop-up)")
                    }
                }
            }
        }

        // Whitelist Banks Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "سرشماره‌های بانکی مجاز (Whitelist)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "فقط پیامک‌های ارسالی از این شماره‌ها/نام‌ها پاپ‌آپ باز می‌کنند:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    whitelist.forEach { (sender, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "$name ($sender)", fontSize = 13.sp)
                            IconButton(onClick = {
                                db.removeWhitelistedBank(sender)
                                whitelist = db.getAllWhitelistedBanks()
                            }) {
                                Text("❌", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSenderId,
                        onValueChange = { newSenderId = it },
                        label = { Text("شناسه یا شماره سرشماره (مثال: ResalatBank)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("نام نمایشی بانک (مثال: بانک رسالت)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newSenderId.isNotBlank()) {
                                db.addWhitelistedBank(newSenderId, if (newDisplayName.isBlank()) newSenderId else newDisplayName)
                                whitelist = db.getAllWhitelistedBanks()
                                newSenderId = ""
                                newDisplayName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("افزودن به لیست مجاز")
                    }
                }
            }
        }

        // Category Management Section (Parmis & Custom Categories)
        item {
            CategoryManagementCard(db = db)
        }

        // In-App Update Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "به‌روزرسانی برنامه (GitHub Updater)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = updateStatusText, fontSize = 13.sp)

                    availableUpdate?.let { info ->
                        if (info.isUpdateAvailable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "نسخه ${info.versionName} آماده دانلود است:\n${info.releaseNotes}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    AppUpdater.downloadAndInstallApk(context, info.downloadUrl, info.versionName)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("دانلود و نصب نسخه جدید")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusText = "در حال بررسی گیت‌هاب..."
                            scope.launch {
                                val result = AppUpdater.checkForUpdate()
                                isCheckingUpdate = false
                                result.onSuccess { info ->
                                    availableUpdate = info
                                    if (info.isUpdateAvailable) {
                                        updateStatusText = "نسخه جدید ${info.versionName} یافت شد!"
                                    } else {
                                        updateStatusText = "برنامه شما به‌روز است (نسخه ${BuildConfig.VERSION_NAME})"
                                    }
                                }.onFailure { e ->
                                    updateStatusText = "خطا در بررسی آپدیت: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isCheckingUpdate) "در حال بررسی..." else "بررسی نسخه جدید")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryManagementCard(db: DatabaseHelper) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var categoryTree by remember { mutableStateOf(listOf<CategoryNode>()) }

    var showAddParentDialog by remember { mutableStateOf(false) }
    var showAddSubDialog by remember { mutableStateOf<Pair<Long, String>?>(null) } // parentId to parentName
    var showEditDialog by remember { mutableStateOf<Pair<Long, String>?>(null) } // id to currentName

    fun reload() {
        categoryTree = db.getCategoryTree(selectedType)
    }

    LaunchedEffect(selectedType) {
        reload()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🗂️ مدیریت دسته‌بندی‌ها (پارمیس و شخصی)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "امکان تغییر نام، افزودن دسته یا زیردسته و حذف موارد:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Type Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = { selectedType = TransactionType.EXPENSE },
                    label = { Text("دسته‌های مخارج (برداشت)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedType == TransactionType.DEPOSIT,
                    onClick = { selectedType = TransactionType.DEPOSIT },
                    label = { Text("دسته‌های درآمد (واریز)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showAddParentDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("+ افزودن دسته اصلی جدید (${if (selectedType == TransactionType.EXPENSE) "مخارج" else "درآمد"})", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Tree List
            categoryTree.forEach { parent ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = parent.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (parent.isCustom) {
                                    Text(
                                        text = "سفارشی",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { showAddSubDialog = Pair(parent.id, parent.name) }) {
                                    Text("➕", fontSize = 12.sp)
                                }
                                IconButton(onClick = { showEditDialog = Pair(parent.id, parent.name) }) {
                                    Text("✏️", fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    db.deleteCategory(parent.id)
                                    reload()
                                }) {
                                    Text("🗑️", fontSize = 12.sp)
                                }
                            }
                        }

                        // Subcategories Chips in horizontal scrolling row
                        if (parent.subCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(parent.subCategories) { sub ->
                                    SuggestionChip(
                                        onClick = { showEditDialog = Pair(sub.id, sub.name) },
                                        label = { Text("${sub.name} ✏️", fontSize = 11.sp) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "بدون زیردسته (روی ➕ بزنید تا زیردسته اضافه کنید)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Parent Dialog
    if (showAddParentDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddParentDialog = false },
            title = { Text("افزودن دسته اصلی جدید", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("نام دسته اصلی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCatName.isNotBlank()) {
                        db.addCategory(newCatName, null, selectedType)
                        showAddParentDialog = false
                        reload()
                    }
                }) {
                    Text("افزودن")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddParentDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Add Subcategory Dialog
    showAddSubDialog?.let { (parentId, parentName) ->
        var newSubName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubDialog = null },
            title = { Text("افزودن زیردسته به «$parentName»", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newSubName,
                    onValueChange = { newSubName = it },
                    label = { Text("نام زیردسته (مثلاً: سیگار، ماست، بنزین)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newSubName.isNotBlank()) {
                        db.addCategory(newSubName, parentId, selectedType)
                        showAddSubDialog = null
                        reload()
                    }
                }) {
                    Text("افزودن")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddSubDialog = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Edit Category Dialog
    showEditDialog?.let { (catId, currentName) ->
        var editName by remember { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("ویرایش نام دسته/زیردسته", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("نام جدید") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank()) {
                        db.updateCategory(catId, editName)
                        showEditDialog = null
                        reload()
                    }
                }) {
                    Text("ذخیره تغییرات")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditDialog = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}
