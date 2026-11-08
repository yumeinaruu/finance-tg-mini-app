package finance.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class CategoryType { EXPENSE, INCOME }

data class Category(
    val id: Long,
    val name: String,
    val emoji: String,
    val type: CategoryType,
) {
    val label: String get() = "$emoji $name"
}

/** An operation as entered by the user, before a category and rate are assigned. */
data class NewOperation(
    val type: CategoryType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val note: String?,
)

data class Operation(
    val id: Long,
    val category: Category,
    val amountMinor: Long,
    val currency: Currency,
    /** BYN per 1 unit of [currency] on [date]. */
    val rate: BigDecimal,
    val amountBaseMinor: Long,
    val date: LocalDate,
    val note: String?,
)

data class Recurring(
    val id: Long,
    val name: String,
    val category: Category,
    val amountMinor: Long,
    val currency: Currency,
    val schedule: Schedule,
    val nextRun: LocalDate,
)

data class BudgetProgress(
    val category: Category,
    val limitMinor: Long,
    val spentMinor: Long,
) {
    val percent: Int get() = (spentMinor * 100 / limitMinor).toInt()
}

data class BudgetAlert(
    val progress: BudgetProgress,
    /** 80 or 100. */
    val threshold: Int,
    /** Days left including today; null for a month other than the current one. */
    val daysLeft: Int?,
)
