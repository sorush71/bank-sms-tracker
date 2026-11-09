package com.sorush.banktracker.model

enum class TransactionType(val titleFa: String) {
    DEPOSIT("واریز"),
    EXPENSE("برداشت")
}

data class Transaction(
    val id: Long = 0,
    val amountToman: Long,
    val type: TransactionType,
    val category: String,
    val bankName: String,
    val timestamp: Long,
    val rawMessage: String,
    val isIgnored: Boolean = false
)

data class Category(
    val name: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)

data class MonthlySummary(
    val yearJalali: Int,
    val monthJalali: Int,
    val monthName: String,
    val totalDepositToman: Long,
    val totalExpenseToman: Long,
    val balanceToman: Long,
    val categoryBreakdown: List<CategoryExpense>
)

data class CategoryExpense(
    val categoryName: String,
    val totalToman: Long,
    val percentage: Float,
    val count: Int
)
