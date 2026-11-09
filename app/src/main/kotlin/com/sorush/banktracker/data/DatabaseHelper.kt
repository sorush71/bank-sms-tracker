package com.sorush.banktracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sorush.banktracker.model.Category
import com.sorush.banktracker.model.CategoryExpense
import com.sorush.banktracker.model.MonthlySummary
import com.sorush.banktracker.model.Transaction
import com.sorush.banktracker.model.TransactionType
import com.sorush.banktracker.util.JalaliCalendar

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "bank_tracker.db"
        const val DATABASE_VERSION = 1

        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_WHITELIST = "bank_whitelist"

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
                bank_name TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                raw_message TEXT NOT NULL,
                is_ignored INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES (
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

        // Prepopulate default bank whitelist
        insertDefaultWhitelist(db, "ResalatBank", "بانک قرض‌الحسنه رسالت")
        insertDefaultWhitelist(db, "B.Pasargad", "بانک پاسارگاد")
        insertDefaultWhitelist(db, "Bank Mellat", "بانک ملت")
        insertDefaultWhitelist(db, "Bank Melli", "بانک ملی")

        // Prepopulate standard categories
        insertInitialCategory(db, "سیگار")
        insertInitialCategory(db, "مواد غذایی")
        insertInitialCategory(db, "قسط و وام")
        insertInitialCategory(db, "حقوق و دریافتی")
        insertInitialCategory(db, "کافه و رستوران")
        insertInitialCategory(db, "تاکسی و حمل‌ونقل")
        insertInitialCategory(db, "خرید اینترنتی")
        insertInitialCategory(db, "اجاره و قبوض")
    }

    private fun insertDefaultWhitelist(db: SQLiteDatabase, senderId: String, name: String) {
        val cv = ContentValues().apply {
            put("sender_id", senderId)
            put("display_name", name)
            put("is_enabled", 1)
        }
        db.insertWithOnConflict(TABLE_WHITELIST, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertInitialCategory(db: SQLiteDatabase, name: String) {
        val cv = ContentValues().apply {
            put("name", name)
            put("usage_count", 0)
            put("last_used_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_CATEGORIES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future updates
    }

    // --- Transactions ---

    fun insertTransaction(
        amountToman: Long,
        type: TransactionType,
        category: String,
        bankName: String,
        timestamp: Long = System.currentTimeMillis(),
        rawMessage: String = "",
        isIgnored: Boolean = false
    ): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("amount_toman", amountToman)
            put("type", type.name)
            put("category", category)
            put("bank_name", bankName)
            put("timestamp", timestamp)
            put("raw_message", rawMessage)
            put("is_ignored", if (isIgnored) 1 else 0)
        }
        val id = db.insert(TABLE_TRANSACTIONS, null, cv)

        if (!isIgnored && category.isNotBlank()) {
            recordCategoryUsage(category)
        }
        return id
    }

    fun getAllTransactions(limit: Int = 100): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, amount_toman, type, category, bank_name, timestamp, raw_message, is_ignored " +
            "FROM $TABLE_TRANSACTIONS ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Transaction(
                        id = it.getLong(0),
                        amountToman = it.getLong(1),
                        type = TransactionType.valueOf(it.getString(2)),
                        category = it.getString(3),
                        bankName = it.getString(4),
                        timestamp = it.getLong(5),
                        rawMessage = it.getString(6),
                        isIgnored = it.getInt(7) == 1
                    )
                )
            }
        }
        return list
    }

    fun getMonthlySummary(jYear: Int, jMonth: Int): MonthlySummary {
        val (startTime, endTime) = JalaliCalendar.getMonthRangeTimestamps(jYear, jMonth)
        val db = readableDatabase

        var totalDeposit = 0L
        var totalExpense = 0L

        // Get total deposit & expense (excluding ignored)
        val totalCursor = db.rawQuery(
            """
            SELECT type, SUM(amount_toman) 
            FROM $TABLE_TRANSACTIONS 
            WHERE is_ignored = 0 AND timestamp BETWEEN ? AND ?
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

        // Category breakdown for expenses
        val categoryList = mutableListOf<CategoryExpense>()
        val catCursor = db.rawQuery(
            """
            SELECT category, SUM(amount_toman), COUNT(id)
            FROM $TABLE_TRANSACTIONS 
            WHERE is_ignored = 0 AND type = 'EXPENSE' AND timestamp BETWEEN ? AND ?
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

    // --- Categories & Auto-suggest ---

    fun recordCategoryUsage(categoryName: String) {
        val cleanName = categoryName.trim()
        if (cleanName.isBlank()) return

        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO $TABLE_CATEGORIES (name, usage_count, last_used_at)
            VALUES (?, 1, ?)
            ON CONFLICT(name) DO UPDATE SET
                usage_count = usage_count + 1,
                last_used_at = excluded.last_used_at
            """.trimIndent(),
            arrayOf(cleanName, System.currentTimeMillis())
        )
    }

    fun getSuggestedCategories(prefix: String = "", limit: Int = 8): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val query: String
        val args: Array<String>

        if (prefix.isBlank()) {
            query = "SELECT name FROM $TABLE_CATEGORIES ORDER BY usage_count DESC, last_used_at DESC LIMIT ?"
            args = arrayOf(limit.toString())
        } else {
            query = "SELECT name FROM $TABLE_CATEGORIES WHERE name LIKE ? ORDER BY usage_count DESC, last_used_at DESC LIMIT ?"
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

    // --- Bank Whitelist ---

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
}
