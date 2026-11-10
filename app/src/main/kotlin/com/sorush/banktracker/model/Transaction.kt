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
    val subCategory: String = "",
    val bankName: String,
    val timestamp: Long,
    val rawMessage: String,
    val isIgnored: Boolean = false,
    val isPending: Boolean = false,
    val isBusiness: Boolean = false,
    val notes: String = "",
    val items: List<TransactionItem> = emptyList()
)

data class TransactionItem(
    val id: Long = 0,
    val transactionId: Long = 0,
    val title: String,
    val amountToman: Long,
    val subCategory: String = "",
    val notes: String = ""
)

data class CategoryNode(
    val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val isCustom: Boolean = false,
    val subCategories: List<CategoryNode> = emptyList()
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
    val personalExpenseToman: Long = 0L,
    val businessExpenseToman: Long = 0L,
    val personalDepositToman: Long = 0L,
    val businessDepositToman: Long = 0L,
    val categoryBreakdown: List<CategoryExpense>
)

data class CategoryExpense(
    val categoryName: String,
    val totalToman: Long,
    val percentage: Float,
    val count: Int
)

data class Installment(
    val id: Long = 0,
    val title: String,
    val bankName: String,
    val amountToman: Long,
    val dueDay: Int, // 1 to 31 (day of Jalali month)
    val totalMonths: Int,
    val paidMonths: Int = 0,
    val isActive: Boolean = true
) {
    val remainingMonths: Int get() = (totalMonths - paidMonths).coerceAtLeast(0)
    val totalRemainingDebtToman: Long get() = remainingMonths.toLong() * amountToman
    val progressPercent: Float get() = if (totalMonths > 0) (paidMonths.toFloat() / totalMonths.toFloat()) else 0f
}

data class InstallmentSummary(
    val totalMonthlyAmountToman: Long,
    val totalRemainingDebtToman: Long,
    val activeCount: Int,
    val bankBreakdown: List<BankInstallmentTotal>
)

data class BankInstallmentTotal(
    val bankName: String,
    val monthlyTotalToman: Long,
    val count: Int
)
