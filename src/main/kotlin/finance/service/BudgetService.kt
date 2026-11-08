package finance.service

import finance.domain.BudgetAlert
import finance.domain.BudgetProgress
import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.Operation
import finance.repo.BudgetRepository
import finance.repo.OperationRepository
import java.time.LocalDate
import java.time.YearMonth

class BudgetService(
    private val budgets: BudgetRepository,
    private val operations: OperationRepository,
) {
    fun set(category: Category, month: YearMonth, limitMinor: Long) = budgets.set(category.id, month, limitMinor)

    fun remove(category: Category, month: YearMonth): Boolean = budgets.delete(category.id, month)

    fun progress(month: YearMonth): List<BudgetProgress> =
        budgets.forMonth(month).map { (category, limit) -> BudgetProgress(category, limit, spent(category.id, month)) }

    fun progress(category: Category, month: YearMonth): BudgetProgress? =
        budgets.find(category.id, month)?.let { BudgetProgress(category, it, spent(category.id, month)) }

    fun copy(from: YearMonth, to: YearMonth): Int = budgets.copy(from, to)

    /**
     * Returns a warning when the operation's category crossed 80% or 100% of its monthly budget
     * for the first time this month.
     */
    fun alertFor(operation: Operation, today: LocalDate): BudgetAlert? {
        val category = operation.category
        if (category.type != CategoryType.EXPENSE) return null
        val month = YearMonth.from(operation.date)
        val progress = progress(category, month) ?: return null
        val threshold = when {
            progress.spentMinor >= progress.limitMinor -> 100
            progress.spentMinor * 100 >= progress.limitMinor * WARNING_PERCENT -> WARNING_PERCENT
            else -> return null
        }
        if (budgets.alerted(category.id, month, threshold)) return null
        listOf(WARNING_PERCENT, 100).filter { it <= threshold }.forEach { budgets.markAlerted(category.id, month, it) }
        val daysLeft = if (month == YearMonth.from(today)) month.lengthOfMonth() - today.dayOfMonth + 1 else null
        return BudgetAlert(progress, threshold, daysLeft)
    }

    private fun spent(categoryId: Long, month: YearMonth) =
        operations.sumByCategory(categoryId, month.atDay(1), month.atEndOfMonth())

    private companion object {
        const val WARNING_PERCENT = 80
    }
}
