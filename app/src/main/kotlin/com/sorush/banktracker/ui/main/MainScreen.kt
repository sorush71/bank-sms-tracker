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
import com.sorush.banktracker.model.MonthlySummary
import com.sorush.banktracker.model.Transaction
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
                    label = { Text("داشبورد ماهانه") },
                    icon = { Text("📊") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("تراکنش‌ها") },
                    icon = { Text("💳") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("بانک‌ها و تنظیمات") },
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
                1 -> TransactionsHistoryTab(db = db)
                2 -> SettingsTab(db = db, onRefreshPermissions = {
                    hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                })
            }
        }
    }
}

@Composable
fun MonthlyDashboardTab(db: DatabaseHelper) {
    val now = remember { JalaliCalendar.now() }
    var currentYear by remember { mutableIntStateOf(now.year) }
    var currentMonth by remember { mutableIntStateOf(now.month) }

    var summary by remember { mutableStateOf<MonthlySummary?>(null) }

    fun refresh() {
        summary = db.getMonthlySummary(currentYear, currentMonth)
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

    val isDeposit = tx.type == TransactionType.DEPOSIT
    val color = if (tx.isIgnored) Color.Gray else if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val sign = if (isDeposit) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (tx.isIgnored) "نادیده گرفته شده" else tx.category,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${tx.bankName} | $date",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$sign${NumberFormat.getNumberInstance(Locale.US).format(tx.amountToman)} تومان",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = color
            )
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
