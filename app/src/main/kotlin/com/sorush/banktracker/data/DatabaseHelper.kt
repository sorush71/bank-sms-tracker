package com.sorush.banktracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sorush.banktracker.model.*
import com.sorush.banktracker.util.JalaliCalendar

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "bank_tracker.db"
        const val DATABASE_VERSION = 3

        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_TRANSACTION_ITEMS = "transaction_items"
        private const val TABLE_CATEGORIES_TREE = "categories_tree"
        private const val TABLE_CATEGORIES_SUGGEST = "categories_suggest"
        private const val TABLE_WHITELIST = "bank_whitelist"
        private const val TABLE_INSTALLMENTS = "installments"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_TRANSACTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_toman INTEGER NOT NULL,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                sub_category TEXT NOT NULL DEFAULT '',
                bank_name TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                raw_message TEXT NOT NULL,
                is_ignored INTEGER NOT NULL DEFAULT 0,
                is_pending INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TRANSACTION_ITEMS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                amount_toman INTEGER NOT NULL,
                sub_category TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (transaction_id) REFERENCES $TABLE_TRANSACTIONS (id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES_TREE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                parent_id INTEGER DEFAULT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                is_custom INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES_SUGGEST (
                name TEXT PRIMARY KEY,
                usage_count INTEGER NOT NULL DEFAULT 1,
                last_used_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_WHITELIST (
                sender_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        createInstallmentsTable(db)

        // Seed Data
        insertDefaultWhitelist(db, "ResalatBank", "بانک قرض‌الحسنه رسالت")
        insertDefaultWhitelist(db, "B.Pasargad", "بانک پاسارگاد")
        insertDefaultWhitelist(db, "Bank Mellat", "بانک ملت")
        insertDefaultWhitelist(db, "Bank Melli", "بانک ملی")

        seedCategoryTree(db)

        insertInitialInstallment(db, "وام قرض‌الحسنه", "بانک قرض‌الحسنه رسالت", 2600000L, 14, 24, 6)
        insertInitialInstallment(db, "قسط تسهیلات", "بانک پاسارگاد", 870000L, 25, 36, 10)
    }

    private fun createInstallmentsTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_INSTALLMENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                bank_name TEXT NOT NULL,
                amount_toman INTEGER NOT NULL,
                due_day INTEGER NOT NULL,
                total_months INTEGER NOT NULL,
                paid_months INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
    }

    private fun seedCategoryTree(db: SQLiteDatabase) {
        // Expense Tree (Parmis style)
        val foodId = addParentCat(db, "خوراک و سوپرمارکت", "EXPENSE")
        addSubCat(db, foodId, "سوپرمارکت و خواربار", "EXPENSE")
        addSubCat(db, foodId, "میوه و سبزیجات", "EXPENSE")
        addSubCat(db, foodId, "پروتئینی (گوشت و مرغ)", "EXPENSE")
        addSubCat(db, foodId, "نان و غلات", "EXPENSE")
        addSubCat(db, foodId, "کافه و رستوران", "EXPENSE")
        addSubCat(db, foodId, "دخانیات و سیگار", "EXPENSE")

        val transportId = addParentCat(db, "حمل و نقل و خودرو", "EXPENSE")
        addSubCat(db, transportId, "اسنپ و تاکسی", "EXPENSE")
        addSubCat(db, transportId, "بنزین و گاز", "EXPENSE")
        addSubCat(db, transportId, "تعمیرات و سرویس", "EXPENSE")
        addSubCat(db, transportId, "بیمه و عوارض", "EXPENSE")

        val houseId = addParentCat(db, "مسکن و قبوض", "EXPENSE")
        addSubCat(db, houseId, "اجاره‌بها", "EXPENSE")
        addSubCat(db, houseId, "شارژ ساختمان", "EXPENSE")
        addSubCat(db, houseId, "قبوض خدماتی", "EXPENSE")
        addSubCat(db, houseId, "تعمیرات و وسایل منزل", "EXPENSE")

        val healthId = addParentCat(db, "بهداشت و درمان", "EXPENSE")
        addSubCat(db, healthId, "لوازم بهداشتی و آرایشی", "EXPENSE")
        addSubCat(db, healthId, "دارو و درمانگاه", "EXPENSE")
        addSubCat(db, healthId, "پزشک و دندانپزشکی", "EXPENSE")

        val clothesId = addParentCat(db, "پوشاک و لوازم شخصی", "EXPENSE")
        addSubCat(db, clothesId, "لباس و پوشاک", "EXPENSE")
        addSubCat(db, clothesId, "کیف و کفش", "EXPENSE")
        addSubCat(db, clothesId, "اکسسوری و وسایل شخصی", "EXPENSE")

        val loanId = addParentCat(db, "اقساط و امور مالی", "EXPENSE")
        addSubCat(db, loanId, "قسط وام و تسهیلات", "EXPENSE")
        addSubCat(db, loanId, "کارمزد بانکی", "EXPENSE")
        addSubCat(db, loanId, "پس‌انداز و بیمه عمر", "EXPENSE")

        val netId = addParentCat(db, "ارتباطات و اینترنت", "EXPENSE")
        addSubCat(db, netId, "بسته اینترنت و شارژ", "EXPENSE")
        addSubCat(db, netId, "اشتراک‌ها و نرم‌افزار", "EXPENSE")

        val funId = addParentCat(db, "تفریح و آموزش", "EXPENSE")
        addSubCat(db, funId, "آموزش و کتاب", "EXPENSE")
        addSubCat(db, funId, "سفر و اقامت", "EXPENSE")
        addSubCat(db, funId, "هدیه و متفرقه", "EXPENSE")

        // Income Tree
        val salaryId = addParentCat(db, "حقوق و دستمزد", "INCOME")
        addSubCat(db, salaryId, "حقوق ماهانه", "INCOME")
        addSubCat(db, salaryId, "اضافه‌کاری و پاداش", "INCOME")

        val freelanceId = addParentCat(db, "درآمد آزاد", "INCOME")
        addSubCat(db, freelanceId, "پروژه‌ای و فریلنسری", "INCOME")
        addSubCat(db, freelanceId, "فروش وسایل", "INCOME")

        val investId = addParentCat(db, "سود و سرمایه‌گذاری", "INCOME")
        addSubCat(db, investId, "سود سپرده بانکی", "INCOME")
        addSubCat(db, investId, "سود سهام و ارز", "INCOME")

        val otherIncId = addParentCat(db, "درآمدهای متفرقه", "INCOME")
        addSubCat(db, otherIncId, "بازپرداخت طلب", "INCOME")
        addSubCat(db, otherIncId, "هدیه نقدی", "INCOME")

        // Seed initial suggestions
        insertInitialSuggestion(db, "سیگار")
        insertInitialSuggestion(db, "سوپرمارکت و خواربار")
        insertInitialSuggestion(db, "قسط وام و تسهیلات")
        insertInitialSuggestion(db, "حقوق ماهانه")
        insertInitialSuggestion(db, "اسنپ و تاکسی")
    }

    private fun addParentCat(db: SQLiteDatabase, name: String, type: String): Long {
        val cv = ContentValues().apply {
            putNull("parent_id")
            put("name", name)
            put("type", type)
            put("is_custom", 0)
        }
        return db.insert(TABLE_CATEGORIES_TREE, null, cv)
    }

    private fun addSubCat(db: SQLiteDatabase, parentId: Long, name: String, type: String): Long {
        val cv = ContentValues().apply {
            put("parent_id", parentId)
            put("name", name)
            put("type", type)
            put("is_custom", 0)
        }
        return db.insert(TABLE_CATEGORIES_TREE, null, cv)
    }

    private fun insertDefaultWhitelist(db: SQLiteDatabase, senderId: String, name: String) {
        val cv = ContentValues().apply {
            put("sender_id", senderId)
            put("display_name", name)
            put("is_enabled", 1)
        }
        db.insertWithOnConflict(TABLE_WHITELIST, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertInitialSuggestion(db: SQLiteDatabase, name: String) {
        val cv = ContentValues().apply {
            put("name", name)
            put("usage_count", 1)
            put("last_used_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_CATEGORIES_SUGGEST, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertInitialInstallment(
        db: SQLiteDatabase,
        title: String,
        bank: String,
        amount: Long,
        dueDay: Int,
        total: Int,
        paid: Int
    ) {
        val cv = ContentValues().apply {
            put("title", title)
            put("bank_name", bank)
            put("amount_toman", amount)
            put("due_day", dueDay)
            put("total_months", total)
            put("paid_months", paid)
            put("is_active", 1)
        }
        db.insert(TABLE_INSTALLMENTS, null, cv)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createInstallmentsTable(db)
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN sub_category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN is_pending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_TRANSACTION_ITEMS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    transaction_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    amount_toman INTEGER NOT NULL,
                    sub_category TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_CATEGORIES_TREE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    parent_id INTEGER DEFAULT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    is_custom INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_CATEGORIES_SUGGEST (
                    name TEXT PRIMARY KEY,
                    usage_count INTEGER NOT NULL DEFAULT 1,
                    last_used_at INTEGER NOT NULL
                )
            """.trimIndent())

            seedCategoryTree(db)
        }
    }

    // --- Transactions & Split Items ---

    fun insertTransaction(
        amountToman: Long,
        type: TransactionType,
        category: String,
        subCategory: String = "",
        bankName: String,
        timestamp: Long = System.currentTimeMillis(),
        rawMessage: String = "",
        isIgnored: Boolean = false,
        isPending: Boolean = false,
        notes: String = "",
        items: List<TransactionItem> = emptyList()
    ): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("amount_toman", amountToman)
            put("type", type.name)
            put("category", category)
            put("sub_category", subCategory)
            put("bank_name", bankName)
            put("timestamp", timestamp)
            put("raw_message", rawMessage)
            put("is_ignored", if (isIgnored) 1 else 0)
            put("is_pending", if (isPending) 1 else 0)
            put("notes", notes)
        }
        val id = db.insert(TABLE_TRANSACTIONS, null, cv)

        if (items.isNotEmpty()) {
            insertItemsForTransaction(db, id, items)
        }

        if (!isIgnored && category.isNotBlank() && !isPending) {
            recordCategoryUsage(if (subCategory.isNotBlank()) subCategory else category)
        }
        return id
    }

    private fun insertItemsForTransaction(db: SQLiteDatabase, txId: Long, items: List<TransactionItem>) {
        for (item in items) {
            val cv = ContentValues().apply {
                put("transaction_id", txId)
                put("title", item.title.trim())
                put("amount_toman", item.amountToman)
                put("sub_category", item.subCategory.trim())
                put("notes", item.notes.trim())
            }
            db.insert(TABLE_TRANSACTION_ITEMS, null, cv)
            if (item.title.isNotBlank()) {
                recordCategoryUsage(item.title)
            }
        }
    }

    fun updateTransactionCategorization(
        txId: Long,
        category: String,
        subCategory: String,
        notes: String,
        isIgnored: Boolean,
        items: List<TransactionItem>
    ) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("category", category)
            put("sub_category", subCategory)
            put("notes", notes)
            put("is_ignored", if (isIgnored) 1 else 0)
            put("is_pending", 0)
        }
        db.update(TABLE_TRANSACTIONS, cv, "id = ?", arrayOf(txId.toString()))

        // Replace items
        db.delete(TABLE_TRANSACTION_ITEMS, "transaction_id = ?", arrayOf(txId.toString()))
        if (items.isNotEmpty()) {
            insertItemsForTransaction(db, txId, items)
        }

        if (!isIgnored) {
            recordCategoryUsage(if (subCategory.isNotBlank()) subCategory else category)
        }
    }

    fun getPendingTransactions(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, amount_toman, type, category, sub_category, bank_name, timestamp, raw_message, is_ignored, is_pending, notes " +
            "FROM $TABLE_TRANSACTIONS WHERE is_pending = 1 AND is_ignored = 0 ORDER BY timestamp DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTransaction(it))
            }
        }
        return list
    }

    fun getAllTransactions(limit: Int = 200): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, amount_toman, type, category, sub_category, bank_name, timestamp, raw_message, is_ignored, is_pending, notes " +
            "FROM $TABLE_TRANSACTIONS ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                val tx = cursorToTransaction(it)
                val items = getItemsForTransaction(tx.id)
                list.add(tx.copy(items = items))
            }
        }
        return list
    }

    fun getItemsForTransaction(txId: Long): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, transaction_id, title, amount_toman, sub_category, notes " +
            "FROM $TABLE_TRANSACTION_ITEMS WHERE transaction_id = ?",
            arrayOf(txId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TransactionItem(
                        id = it.getLong(0),
                        transactionId = it.getLong(1),
                        title = it.getString(2),
                        amountToman = it.getLong(3),
                        subCategory = it.getString(4),
                        notes = it.getString(5)
                    )
                )
            }
        }
        return list
    }

    private fun cursorToTransaction(c: android.database.Cursor): Transaction {
        return Transaction(
            id = c.getLong(0),
            amountToman = c.getLong(1),
            type = TransactionType.valueOf(c.getString(2)),
            category = c.getString(3),
            subCategory = c.getString(4),
            bankName = c.getString(5),
            timestamp = c.getLong(6),
            rawMessage = c.getString(7),
            isIgnored = c.getInt(8) == 1,
            isPending = c.getInt(9) == 1,
            notes = c.getString(10)
        )
    }

    fun getMonthlySummary(jYear: Int, jMonth: Int): MonthlySummary {
        val (startTime, endTime) = JalaliCalendar.getMonthRangeTimestamps(jYear, jMonth)
        val db = readableDatabase

        var totalDeposit = 0L
        var totalExpense = 0L

        val totalCursor = db.rawQuery(
            """
            SELECT type, SUM(amount_toman) 
            FROM $TABLE_TRANSACTIONS 
            WHERE is_ignored = 0 AND is_pending = 0 AND timestamp BETWEEN ? AND ?
            GROUP BY type
            """.trimIndent(),
            arrayOf(startTime.toString(), endTime.toString())
        )
        totalCursor.use {
            while (it.moveToNext()) {
                val type = it.getString(0)
                val sum = it.getLong(1)
                if (type == TransactionType.DEPOSIT.name) {
                    totalDeposit = sum
                } else if (type == TransactionType.EXPENSE.name) {
                    totalExpense = sum
                }
            }
        }

        val categoryList = mutableListOf<CategoryExpense>()
        val catCursor = db.rawQuery(
            """
            SELECT category, SUM(amount_toman), COUNT(id)
            FROM $TABLE_TRANSACTIONS 
            WHERE is_ignored = 0 AND is_pending = 0 AND type = 'EXPENSE' AND timestamp BETWEEN ? AND ?
            GROUP BY category
            ORDER BY SUM(amount_toman) DESC
            """.trimIndent(),
            arrayOf(startTime.toString(), endTime.toString())
        )
        catCursor.use {
            while (it.moveToNext()) {
                val catName = it.getString(0)
                val sum = it.getLong(1)
                val count = it.getInt(2)
                val percentage = if (totalExpense > 0) (sum.toFloat() / totalExpense.toFloat()) * 100f else 0f
                categoryList.add(CategoryExpense(catName, sum, percentage, count))
            }
        }

        val monthName = JalaliCalendar.MONTH_NAMES.getOrElse(jMonth - 1) { "" }
        return MonthlySummary(
            yearJalali = jYear,
            monthJalali = jMonth,
            monthName = monthName,
            totalDepositToman = totalDeposit,
            totalExpenseToman = totalExpense,
            balanceToman = totalDeposit - totalExpense,
            categoryBreakdown = categoryList
        )
    }

    // --- Category Tree Management (Parmis & Custom) ---

    fun getCategoryTree(type: TransactionType): List<CategoryNode> {
        val db = readableDatabase
        val parents = mutableListOf<CategoryNode>()

        val parentCursor = db.rawQuery(
            "SELECT id, name, is_custom FROM $TABLE_CATEGORIES_TREE WHERE parent_id IS NULL AND type = ? ORDER BY id ASC",
            arrayOf(type.name)
        )
        parentCursor.use {
            while (it.moveToNext()) {
                val pId = it.getLong(0)
                val pName = it.getString(1)
                val pCustom = it.getInt(2) == 1

                val subs = mutableListOf<CategoryNode>()
                val subCursor = db.rawQuery(
                    "SELECT id, name, is_custom FROM $TABLE_CATEGORIES_TREE WHERE parent_id = ? ORDER BY id ASC",
                    arrayOf(pId.toString())
                )
                subCursor.use { sc ->
                    while (sc.moveToNext()) {
                        subs.add(
                            CategoryNode(
                                id = sc.getLong(0),
                                parentId = pId,
                                name = sc.getString(1),
                                type = type,
                                isCustom = sc.getInt(2) == 1
                            )
                        )
                    }
                }
                parents.add(CategoryNode(id = pId, parentId = null, name = pName, type = type, isCustom = pCustom, subCategories = subs))
            }
        }
        return parents
    }

    fun addCategory(name: String, parentId: Long?, type: TransactionType): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            if (parentId != null) put("parent_id", parentId) else putNull("parent_id")
            put("name", name.trim())
            put("type", type.name)
            put("is_custom", 1)
        }
        val id = db.insert(TABLE_CATEGORIES_TREE, null, cv)
        recordCategoryUsage(name.trim())
        return id
    }

    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_CATEGORIES_TREE, "id = ? OR parent_id = ?", arrayOf(id.toString(), id.toString()))
    }

    fun updateCategory(id: Long, newName: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", newName.trim())
        }
        db.update(TABLE_CATEGORIES_TREE, cv, "id = ?", arrayOf(id.toString()))
        recordCategoryUsage(newName.trim())
    }

    fun recordCategoryUsage(categoryName: String) {
        val cleanName = categoryName.trim()
        if (cleanName.isBlank()) return

        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO $TABLE_CATEGORIES_SUGGEST (name, usage_count, last_used_at)
            VALUES (?, 1, ?)
            ON CONFLICT(name) DO UPDATE SET
                usage_count = usage_count + 1,
                last_used_at = excluded.last_used_at
            """.trimIndent(),
            arrayOf<Any>(cleanName, System.currentTimeMillis())
        )
    }

    fun getSuggestedCategories(prefix: String = "", limit: Int = 8): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val query: String
        val args: Array<String>

        if (prefix.isBlank()) {
            query = "SELECT name FROM $TABLE_CATEGORIES_SUGGEST ORDER BY usage_count DESC, last_used_at DESC LIMIT ?"
            args = arrayOf(limit.toString())
        } else {
            query = "SELECT name FROM $TABLE_CATEGORIES_SUGGEST WHERE name LIKE ? ORDER BY usage_count DESC, last_used_at DESC LIMIT ?"
            args = arrayOf("$prefix%", limit.toString())
        }

        val cursor = db.rawQuery(query, args)
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    // --- Whitelist & Installments ---

    fun getWhitelistedSenders(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT sender_id FROM $TABLE_WHITELIST WHERE is_enabled = 1", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun isSenderWhitelisted(sender: String): Boolean {
        val list = getWhitelistedSenders()
        val normalized = sender.trim().lowercase()
        return list.any { whitelisted ->
            val w = whitelisted.trim().lowercase()
            normalized.contains(w) || w.contains(normalized)
        }
    }

    fun addWhitelistedBank(senderId: String, displayName: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("sender_id", senderId.trim())
            put("display_name", displayName.trim())
            put("is_enabled", 1)
        }
        db.insertWithOnConflict(TABLE_WHITELIST, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeWhitelistedBank(senderId: String) {
        val db = writableDatabase
        db.delete(TABLE_WHITELIST, "sender_id = ?", arrayOf(senderId))
    }

    fun getAllWhitelistedBanks(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT sender_id, display_name FROM $TABLE_WHITELIST", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(Pair(it.getString(0), it.getString(1)))
            }
        }
        return list
    }

    fun insertInstallment(
        title: String,
        bankName: String,
        amountToman: Long,
        dueDay: Int,
        totalMonths: Int,
        paidMonths: Int = 0
    ): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title.trim())
            put("bank_name", bankName.trim())
            put("amount_toman", amountToman)
            put("due_day", dueDay.coerceIn(1, 31))
            put("total_months", totalMonths.coerceAtLeast(1))
            put("paid_months", paidMonths.coerceAtLeast(0))
            put("is_active", 1)
        }
        return db.insert(TABLE_INSTALLMENTS, null, cv)
    }

    fun getAllInstallments(): List<Installment> {
        val list = mutableListOf<Installment>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, title, bank_name, amount_toman, due_day, total_months, paid_months, is_active " +
            "FROM $TABLE_INSTALLMENTS WHERE is_active = 1 ORDER BY due_day ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Installment(
                        id = it.getLong(0),
                        title = it.getString(1),
                        bankName = it.getString(2),
                        amountToman = it.getLong(3),
                        dueDay = it.getInt(4),
                        totalMonths = it.getInt(5),
                        paidMonths = it.getInt(6),
                        isActive = it.getInt(7) == 1
                    )
                )
            }
        }
        return list
    }

    fun incrementInstallmentPaid(id: Long) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_INSTALLMENTS SET paid_months = MIN(paid_months + 1, total_months) WHERE id = ?",
            arrayOf(id.toString())
        )
    }

    fun deleteInstallment(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_INSTALLMENTS, "id = ?", arrayOf(id.toString()))
    }

    fun getInstallmentSummary(): InstallmentSummary {
        val all = getAllInstallments()
        var totalMonthly = 0L
        var totalRemainingDebt = 0L

        val bankMap = mutableMapOf<String, Pair<Long, Int>>()

        for (inst in all) {
            totalMonthly += inst.amountToman
            totalRemainingDebt += inst.totalRemainingDebtToman

            val existing = bankMap.getOrDefault(inst.bankName, Pair(0L, 0))
            bankMap[inst.bankName] = Pair(existing.first + inst.amountToman, existing.second + 1)
        }

        val bankBreakdown = bankMap.map { (bank, pair) ->
            BankInstallmentTotal(
                bankName = bank,
                monthlyTotalToman = pair.first,
                count = pair.second
            )
        }.sortedByDescending { it.monthlyTotalToman }

        return InstallmentSummary(
            totalMonthlyAmountToman = totalMonthly,
            totalRemainingDebtToman = totalRemainingDebt,
            activeCount = all.size,
            bankBreakdown = bankBreakdown
        )
    }
}
