package finance.service

import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.Operation
import finance.repo.OperationRepository
import java.time.LocalDate

data class CategoryTotal(val category: Category, val totalMinor: Long)

data class Summary(
    val from: LocalDate,
    val to: LocalDate,
    val operations: List<Operation>,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val expenseByCategory: List<CategoryTotal>,
) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

class ReportService(private val operations: OperationRepository) {

    fun summary(from: LocalDate, to: LocalDate): Summary {
        val ops = operations.between(from, to)
        val (income, expense) = ops.partition { it.category.type == CategoryType.INCOME }
        return Summary(
            from = from,
            to = to,
            operations = ops,
            incomeMinor = income.sumOf { it.amountBaseMinor },
            expenseMinor = expense.sumOf { it.amountBaseMinor },
            expenseByCategory = expense.groupBy { it.category.id }
                .map { (_, list) -> CategoryTotal(list.first().category, list.sumOf { it.amountBaseMinor }) }
                .sortedByDescending { it.totalMinor },
        )
    }
}
