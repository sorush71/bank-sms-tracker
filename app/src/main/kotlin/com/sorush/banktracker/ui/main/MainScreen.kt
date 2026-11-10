package com.sorush.banktracker.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sorush.banktracker.BuildConfig
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.model.CategoryExpense
import com.sorush.banktracker.model.CategoryNode
import com.sorush.banktracker.model.MonthlySummary
import com.sorush.banktracker.model.Transaction
import com.sorush.banktracker.model.TransactionItem
import com.sorush.banktracker.model.TransactionType
import com.sorush.banktracker.parser.ParsedTransaction
import com.sorush.banktracker.parser.TransactionParser
import com.sorush.banktracker.updater.AppUpdater
import com.sorush.banktracker.updater.UpdateInfo
import com.sorush.banktracker.util.DeviceCompatibilityHelper
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
    val prefs = remember { context.getSharedPreferences("bank_tracker_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: داشبورد, 1: اقساط, 2: تراکنش‌ها, 3: تنظیمات
    var refreshTrigger by remember { mutableIntStateOf(0) }

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
    var isSetupCompleted by remember {
        mutableStateOf(prefs.getBoolean("setup_completed", false))
    }
    var isBatteryIgnored by remember {
        mutableStateOf(DeviceCompatibilityHelper.isBatteryOptimizationIgnored(context))
    }
    var showSetupDialog by remember {
        mutableStateOf(!isSetupCompleted || !hasSmsPermission || !hasOverlayPermission)
    }

    // Lifecycle observer to re-check permissions live whenever returning from phone Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                isBatteryIgnored = DeviceCompatibilityHelper.isBatteryOptimizationIgnored(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermission = results[Manifest.permission.RECEIVE_SMS] == true
    }

    fun requestSms() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Mandatory Setup Modal Dialog
    if (showSetupDialog) {
        MandatorySetupDialog(
            hasSmsPermission = hasSmsPermission,
            hasOverlayPermission = hasOverlayPermission,
            isBatteryIgnored = isBatteryIgnored,
            onRequestSmsPermission = { requestSms() },
            onRequestOverlayPermission = { DeviceCompatibilityHelper.openOverlaySettings(context) },
            onOpenAutostart = { DeviceCompatibilityHelper.openAutostartSettings(context) },
            onRequestBatteryOptimization = { DeviceCompatibilityHelper.requestIgnoreBatteryOptimizations(context) },
            onOpenPopupInBackground = { DeviceCompatibilityHelper.openPopupInBackgroundSettings(context) },
            onCompleteSetup = {
                prefs.edit().putBoolean("setup_completed", true).apply()
                isSetupCompleted = true
                showSetupDialog = false
            }
        )
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
                                    onClick = { requestSms() },
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
                0 -> MonthlyDashboardTab(db = db, refreshTrigger = refreshTrigger)
                1 -> InstallmentsTab(db = db)
                2 -> TransactionsHistoryTab(db = db, refreshTrigger = refreshTrigger)
                3 -> SettingsTab(
                    db = db,
                    hasSmsPermission = hasSmsPermission,
                    hasOverlayPermission = hasOverlayPermission,
                    isBatteryIgnored = isBatteryIgnored,
                    onRequestSmsPermission = { requestSms() },
                    onRequestOverlayPermission = { DeviceCompatibilityHelper.openOverlaySettings(context) },
                    onRequestBatteryOptimization = { DeviceCompatibilityHelper.requestIgnoreBatteryOptimizations(context) },
                    onOpenSetupDialog = { showSetupDialog = true }
                )
            }
        }
    }
}

@Composable
fun MonthlyDashboardTab(db: DatabaseHelper, refreshTrigger: Int = 0) {
    val context = LocalContext.current
    val now = remember { JalaliCalendar.now() }
    var currentYear by remember { mutableIntStateOf(now.year) }
    var currentMonth by remember { mutableIntStateOf(now.month) }

    // Filter categories: null for all, false for personal, true for business
    var categoryModeFilter by remember { mutableStateOf<Boolean?>(null) }

    var summary by remember { mutableStateOf<MonthlySummary?>(null) }
    var pendingTransactions by remember { mutableStateOf(listOf<Transaction>()) }

    fun refresh() {
        summary = db.getMonthlySummary(currentYear, currentMonth, filterBusiness = categoryModeFilter)
        pendingTransactions = db.getPendingTransactions()
    }

    LaunchedEffect(currentYear, currentMonth, categoryModeFilter, refreshTrigger) {
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
                                                    txId = pTx.id,
                                                    category = "نادیده گرفته شده",
                                                    subCategory = "",
                                                    notes = "نادیده گرفته شد",
                                                    isIgnored = true,
                                                    isBusiness = false,
                                                    items = emptyList()
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

        // Business vs Personal Breakdown Card
        item {
            summary?.let { s ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "تفکیک مخارج کاری و شخصی این ماه:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Personal Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("🏠 مخارج شخصی", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4527A0))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${NumberFormat.getNumberInstance(Locale.US).format(s.personalExpenseToman)} ت",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF311B92)
                                    )
                                }
                            }

                            // Business Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("💼 کاری (فست‌فود)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${NumberFormat.getNumberInstance(Locale.US).format(s.businessExpenseToman)} ت",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF004D40)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Breakdown Header with Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دسته‌بندی مخارج:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = categoryModeFilter == null,
                        onClick = { categoryModeFilter = null },
                        label = { Text("همه", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = categoryModeFilter == false,
                        onClick = { categoryModeFilter = false },
                        label = { Text("🏠 شخصی", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = categoryModeFilter == true,
                        onClick = { categoryModeFilter = true },
                        label = { Text("💼 کاری", fontSize = 10.sp) }
                    )
                }
            }
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
                        com.sorush.banktracker.receiver.InstallmentReminderReceiver.triggerTestReminder(context)
                        Toast.makeText(context, "اعلان یادآور قسط ارسال شد", Toast.LENGTH_SHORT).show()
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

data class ScannedSmsItem(
    val smsId: Long,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val parsed: ParsedTransaction,
    val alreadySaved: Boolean,
    var isSelected: Boolean,
    var isBusiness: Boolean
)

@Composable
fun TransactionsHistoryTab(db: DatabaseHelper, refreshTrigger: Int = 0) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf(listOf<Transaction>()) }
    var searchQuery by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf<Boolean?>(null) } // null: همه, false: شخصی, true: کاری
    var showScannerDialog by remember { mutableStateOf(false) }

    fun refresh() {
        transactions = db.getAllTransactions(limit = 300)
    }

    LaunchedEffect(refreshTrigger) {
        refresh()
    }

    val filtered = remember(transactions, searchQuery, modeFilter) {
        transactions.filter { tx ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                tx.category.contains(searchQuery, ignoreCase = true) ||
                tx.bankName.contains(searchQuery, ignoreCase = true) ||
                tx.amountToman.toString().contains(searchQuery)
            }
            val matchesMode = when (modeFilter) {
                true -> tx.isBusiness
                false -> !tx.isBusiness
                null -> true
            }
            matchesSearch && matchesMode
        }
    }

    // Permission launcher for READ_SMS
    val readSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showScannerDialog = true
        } else {
            Toast.makeText(context, "برای اسکن پیامک‌ها، مجوز خواندن پیامک لازم است.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showScannerDialog) {
        SmsScannerDialog(
            db = db,
            onDismiss = { showScannerDialog = false },
            onImportComplete = { count ->
                showScannerDialog = false
                refresh()
                Toast.makeText(context, "$count تراکنش با موفقیت به سیستم اضافه شد", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Row: Header + Scan SMS Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تاریخچه تراکنش‌ها",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Button(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        showScannerDialog = true
                    } else {
                        readSmsLauncher.launch(Manifest.permission.READ_SMS)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("📥 اسکن پیامک‌های گذشته", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("جستجو در دسته‌ها، بانک‌ها یا مبالغ...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mode Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("نمایش:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = modeFilter == null,
                onClick = { modeFilter = null },
                label = { Text("همه (${transactions.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = modeFilter == false,
                onClick = { modeFilter = false },
                label = { Text("🏠 شخصی", fontSize = 11.sp) }
            )
            FilterChip(
                selected = modeFilter == true,
                onClick = { modeFilter = true },
                label = { Text("💼 کاری", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "تراکنشی یافت نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        onToggleBusiness = {
                            val newMode = !tx.isBusiness
                            db.updateTransactionBusinessMode(tx.id, newMode)
                            refresh()
                            val msg = if (newMode) "تراکنش به هزینه‌های کاری تغییر یافت" else "تراکنش به هزینه‌های شخصی تغییر یافت"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction, onToggleBusiness: (() -> Unit)? = null) {
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

                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${tx.bankName} | $date",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Mode chip badge (clickable to switch)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (tx.isBusiness) Color(0xFFE0F2F1) else Color(0xFFEDE7F6))
                                .clickable { onToggleBusiness?.invoke() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (tx.isBusiness) "💼 کاری" else "🏠 شخصی",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.isBusiness) Color(0xFF00695C) else Color(0xFF512DA8)
                            )
                        }
                    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerDialog(
    db: DatabaseHelper,
    onDismiss: () -> Unit,
    onImportComplete: (Int) -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(true) }
    val scannedList = remember { mutableStateListOf<ScannedSmsItem>() }
    var filterType by remember { mutableStateOf<TransactionType?>(null) } // null: همه, DEPOSIT, EXPENSE
    var showOnlyNew by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isScanning = true
        val results = mutableListOf<ScannedSmsItem>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date"),
                null,
                null,
                "date DESC LIMIT 400"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val smsId = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val sender = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val timestamp = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()

                    val parsed = TransactionParser.parse(sender, body)
                    if (parsed != null) {
                        val alreadySaved = db.existsTransaction(body, timestamp)
                        results.add(
                            ScannedSmsItem(
                                smsId = smsId,
                                sender = sender,
                                body = body,
                                timestamp = timestamp,
                                parsed = parsed,
                                alreadySaved = alreadySaved,
                                isSelected = !alreadySaved,
                                isBusiness = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scannedList.clear()
        scannedList.addAll(results)
        isScanning = false
    }

    val visibleItems = remember(scannedList.toList(), filterType, showOnlyNew) {
        scannedList.filter { item ->
            val matchesType = filterType == null || item.parsed.type == filterType
            val matchesNew = if (showOnlyNew) !item.alreadySaved else true
            matchesType && matchesNew
        }
    }

    val selectedCount = scannedList.count { it.isSelected && !it.alreadySaved }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📥 اسکن پیامک‌های بانکی گذشته",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("در حال جستجو و تحلیل پیامک‌های بانکی...", fontSize = 13.sp)
                        }
                    }
                } else {
                    val totalFound = scannedList.size
                    val newCount = scannedList.count { !it.alreadySaved }

                    Text(
                        text = "$totalFound پیامک بانکی کشف شد ($newCount مورد جدید)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filters & Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterType == null,
                            onClick = { filterType = null },
                            label = { Text("همه", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = filterType == TransactionType.EXPENSE,
                            onClick = { filterType = TransactionType.EXPENSE },
                            label = { Text("برداشت", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = filterType == TransactionType.DEPOSIT,
                            onClick = { filterType = TransactionType.DEPOSIT },
                            label = { Text("واریز", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = showOnlyNew,
                            onClick = { showOnlyNew = !showOnlyNew },
                            label = { Text("فقط جدیدها", fontSize = 10.sp) }
                        )
                    }

                    // Batch select buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = {
                                scannedList.forEachIndexed { idx, it ->
                                    if (!it.alreadySaved) scannedList[idx] = it.copy(isSelected = true)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("انتخاب همه جدیدها", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = {
                                scannedList.forEachIndexed { idx, it ->
                                    scannedList[idx] = it.copy(isSelected = false)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("لغو انتخاب", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                scannedList.forEachIndexed { idx, it ->
                                    if (it.isSelected) scannedList[idx] = it.copy(isBusiness = !it.isBusiness)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("تغییر کاری/شخصی", fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showOnlyNew && newCount == 0)
                                    "تمام پیامک‌های بانکی قبلاً در دیتابیس ثبت شده‌اند."
                                else "هیچ تراکنشی با این فیلتر یافت نشد.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(visibleItems, key = { it.smsId }) { item ->
                                val realIndex = scannedList.indexOfFirst { it.smsId == item.smsId }
                                val isDeposit = item.parsed.type == TransactionType.DEPOSIT
                                val sign = if (isDeposit) "+" else "-"
                                val color = if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828)

                                val jDate = remember(item.timestamp) {
                                    val j = JalaliCalendar.fromTimestamp(item.timestamp)
                                    val sdf = SimpleDateFormat("HH:mm", Locale.US)
                                    "${j.formatted()} ${sdf.format(Date(item.timestamp))}"
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.alreadySaved) Color(0xFFF5F5F5) else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.isSelected,
                                            enabled = !item.alreadySaved,
                                            onCheckedChange = { chk ->
                                                if (realIndex >= 0) {
                                                    scannedList[realIndex] = item.copy(isSelected = chk)
                                                }
                                            }
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = item.parsed.bankName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "$sign${NumberFormat.getNumberInstance(Locale.US).format(item.parsed.amountToman)} ت",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = color
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = jDate,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                if (item.alreadySaved) {
                                                    Text(
                                                        text = "قبلاً ثبت شده",
                                                        fontSize = 10.sp,
                                                        color = Color.Gray,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (item.isBusiness) Color(0xFFE0F2F1) else Color(0xFFEDE7F6))
                                                            .clickable {
                                                                if (realIndex >= 0) {
                                                                    scannedList[realIndex] = item.copy(isBusiness = !item.isBusiness)
                                                                }
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (item.isBusiness) "💼 کاری" else "🏠 شخصی",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (item.isBusiness) Color(0xFF00695C) else Color(0xFF512DA8)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Action Button
                    Button(
                        onClick = {
                            val toImport = scannedList.filter { it.isSelected && !it.alreadySaved }
                            toImport.forEach { item ->
                                db.insertTransaction(
                                    amountToman = item.parsed.amountToman,
                                    type = item.parsed.type,
                                    category = "تراکنش بانکی",
                                    subCategory = item.parsed.bankName,
                                    bankName = item.parsed.bankName,
                                    timestamp = item.timestamp,
                                    rawMessage = item.body,
                                    isIgnored = false,
                                    isPending = false,
                                    isBusiness = item.isBusiness,
                                    notes = "وارد شده از اسکن پیامک"
                                )
                            }
                            onImportComplete(toImport.size)
                        },
                        enabled = selectedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (selectedCount > 0) "📥 درون‌ریزی $selectedCount تراکنش انتخاب‌شده" else "هیچ تراکنشی انتخاب نشده",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MandatorySetupDialog(
    hasSmsPermission: Boolean,
    hasOverlayPermission: Boolean,
    isBatteryIgnored: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAutostart: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenPopupInBackground: () -> Unit,
    onCompleteSetup: () -> Unit
) {
    var autostartVisited by remember { mutableStateOf(false) }
    var popupVisited by remember { mutableStateOf(false) }
    val isXiaomi = remember { DeviceCompatibilityHelper.isXiaomiDevice() }
    val deviceBrand = remember { DeviceCompatibilityHelper.getDeviceBrandName() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* Modal dialog: requires user acknowledgment or valid permissions */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "راه‌اندازی و مجوزهای ضروری",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "برای اینکه برنامه بتواند بدون وقفه پیامک‌های بانکی را بخواند و پنجره ثبت هزینه را روی گوشی شما ($deviceBrand) نمایش دهد، تنظیم موارد زیر الزامی است:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // ۱. مجوز پیامک
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasSmsPermission) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "۱. مجوز پیامک و اعلان‌ها",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (hasSmsPermission) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                                if (hasSmsPermission) {
                                    Text("✅ فعال است", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "برای رهگیری خودکار مبالغ واریز و برداشت بانکی",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!hasSmsPermission) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onRequestSmsPermission,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                                ) {
                                    Text("درخواست مجوز پیامک", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ۲. مجوز پنجره شناور (Overlay)
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasOverlayPermission) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "۲. مجوز پنجره شناور (Overlay)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (hasOverlayPermission) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                                if (hasOverlayPermission) {
                                    Text("✅ فعال است", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "برای باز شدن خودکار پاپ‌آپ روی برنامه‌ها به محض دریافت پیامک",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!hasOverlayPermission) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onRequestOverlayPermission,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                                ) {
                                    Text("فعال‌سازی مجوز پاپ‌آپ (Overlay)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ۳. باتری بدون محدودیت
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBatteryIgnored) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "۳. باتری بدون محدودیت (No restrictions)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isBatteryIgnored) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                                if (isBatteryIgnored) {
                                    Text("✅ فعال است", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "جلوگیری از توقف و به خواب رفتن برنامه در پس‌زمینه توسط سیستم‌عامل",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isBatteryIgnored) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onRequestBatteryOptimization,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                                ) {
                                    Text("تنظیم باتری روی بدون محدودیت", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ۴. شروع خودکار ویژه سازنده
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (autostartVisited) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "۴. شروع خودکار در پس‌زمینه ($deviceBrand)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (autostartVisited) {
                                    Text("✔️ مشاهده شد", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "برای ماندن دائمی سرویس در پس‌زمینه پس از بستن برنامه‌ها در گوشی $deviceBrand",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    autostartVisited = true
                                    onOpenAutostart()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تنظیم شروع خودکار ($deviceBrand)", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ۵. پاپ‌آپ در پس‌زمینه شیائومی
                if (isXiaomi) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (popupVisited) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "۵. مجوز پاپ‌آپ در پس‌زمینه (HyperOS / MIUI)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (popupVisited) {
                                        Text("✔️ مشاهده شد", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "گزینه Display pop-up windows while running in background در شیائومی",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        popupVisited = true
                                        onOpenPopupInBackground()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("فعال‌سازی پاپ‌آپ پس‌زمینه شیائومی", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!hasSmsPermission || !hasOverlayPermission) {
                        Toast.makeText(
                            context,
                            "لطفاً ابتدا مجوزهای ۱ و ۲ (پیامک و پاپ‌آپ) را تایید کنید.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        onCompleteSetup()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasSmsPermission && hasOverlayPermission) MaterialTheme.colorScheme.primary else Color.Gray
                )
            ) {
                Text(
                    text = if (hasSmsPermission && hasOverlayPermission) "✅ ذخیره و ورود به برنامه" else "تأیید و ورود (نیاز به مجوزهای ۱ و ۲)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun SettingsTab(
    db: DatabaseHelper,
    hasSmsPermission: Boolean,
    hasOverlayPermission: Boolean,
    isBatteryIgnored: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenSetupDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var whitelist by remember { mutableStateOf(db.getAllWhitelistedBanks()) }
    var newSenderId by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }

    var updateStatusText by remember { mutableStateOf("") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. In-App Update Section (GitHub Updater) ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔄 به‌روزرسانی برنامه (GitHub)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "نسخه فعلی: v${BuildConfig.VERSION_NAME}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "استعلام و دانلود مستقیم جدیدترین نسخه از مخزن گیت‌هاب (sorush71/bank-sms-tracker):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusText = "در حال بررسی گیت‌هاب..."
                            scope.launch {
                                val result = AppUpdater.checkForUpdate()
                                isCheckingUpdate = false
                                result.onSuccess { info ->
                                    availableUpdate = info
                                    if (info.isUpdateAvailable) {
                                        updateStatusText = "🎉 نسخه جدید ${info.versionName} آماده دانلود است!"
                                    } else {
                                        updateStatusText = "✅ برنامه شما کاملاً به‌روز است (نسخه ${BuildConfig.VERSION_NAME})"
                                    }
                                }.onFailure { e ->
                                    updateStatusText = "⚠️ خطا در بررسی آپدیت: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال بررسی گیت‌هاب...", fontSize = 13.sp)
                        } else {
                            Text("🔄 بررسی نسخه جدید در گیت‌هاب", fontSize = 13.sp)
                        }
                    }

                    if (updateStatusText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = if (availableUpdate?.isUpdateAvailable == true) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = updateStatusText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (availableUpdate?.isUpdateAvailable == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )

                                availableUpdate?.let { info ->
                                    if (info.isUpdateAvailable) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "تغییرات نسخه ${info.versionName}:\n${info.releaseNotes}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    AppUpdater.downloadAndInstallApk(context, info.downloadUrl, info.versionName)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                            ) {
                                                Text("⬇️ دانلود و نصب خودکار", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    AppUpdater.openInBrowser(context, info.downloadUrl)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("🌐 دانلود از مرورگر", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Permissions & Device Compatibility Section ---
        item {
            val deviceBrand = remember { DeviceCompatibilityHelper.getDeviceBrandName() }
            val isXiaomi = remember { DeviceCompatibilityHelper.isXiaomiDevice() }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚙️ تنظیمات و بهینه‌سازی سیستم ($deviceBrand)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "برای اطمینان از بالا آمدن بی‌درنگ پاپ‌آپ و عدم توقف در پس‌زمینه:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Permissions quick indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (hasSmsPermission) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (hasSmsPermission) "✅ پیامک: فعال" else "❌ پیامک",
                                modifier = Modifier.padding(6.dp),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                color = if (hasSmsPermission) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                        Surface(
                            color = if (hasOverlayPermission) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (hasOverlayPermission) "✅ پاپ‌آپ: فعال" else "❌ پاپ‌آپ",
                                modifier = Modifier.padding(6.dp),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                color = if (hasOverlayPermission) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                        Surface(
                            color = if (isBatteryIgnored) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isBatteryIgnored) "✅ باتری: آزاد" else "⚠️ باتری",
                                modifier = Modifier.padding(6.dp),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                color = if (isBatteryIgnored) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenSetupDialog,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("🚀 بازبینی فرآیند راه‌اندازی و مجوزها", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { DeviceCompatibilityHelper.openAutostartSettings(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Autostart", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onRequestBatteryOptimization,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("باتری", fontSize = 11.sp)
                        }
                        if (isXiaomi) {
                            OutlinedButton(
                                onClick = { DeviceCompatibilityHelper.openPopupInBackgroundSettings(context) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("پاپ‌آپ شیائومی", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Whitelist Banks Section ---
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

        // --- 4. Category Management Section ---
        item {
            CategoryManagementCard(db = db)
        }

        // --- 5. Test Popup Simulator Section ---
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
                                    putExtra("EXTRA_AMOUNT_TOMAN", 260000L)
                                    putExtra("EXTRA_TYPE", TransactionType.EXPENSE.name)
                                    putExtra("EXTRA_BANK_NAME", "بانک قرض‌الحسنه رسالت")
                                    putExtra("EXTRA_RAW_MESSAGE", "10.14423005.1\n-2,600,000\n08/14 00:23")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("🔴 تست برداشت (-۲۶۰ ه)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, com.sorush.banktracker.ui.popup.OverlayDialogActivity::class.java).apply {
                                    putExtra("EXTRA_AMOUNT_TOMAN", 2600000L)
                                    putExtra("EXTRA_TYPE", TransactionType.DEPOSIT.name)
                                    putExtra("EXTRA_BANK_NAME", "بانک پاسارگاد")
                                    putExtra("EXTRA_RAW_MESSAGE", "777.888.27870097.1\n+26,000,000\n08/18 02:56")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("🟢 تست واریز (+۲.۶ م)", fontSize = 11.sp)
                        }
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
                text = "🗂️ مدیریت دسته‌بندی‌ها",
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
