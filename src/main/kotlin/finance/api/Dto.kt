package finance.api

import finance.domain.Category
import finance.domain.Operation
import finance.domain.Recurring
import finance.domain.Schedule
import kotlinx.serialization.Serializable

/** Amounts are in minor units (kopecks/cents); dates are ISO yyyy-MM-dd, months yyyy-MM. */

@Serializable
data class CategoryDto(val id: Long, val name: String, val emoji: String, val type: String, val archived: Boolean)

@Serializable
data class MetaDto(
    val baseCurrency: String,
    val currencies: List<String>,
    val today: String,
    val categories: List<CategoryDto>,
)

@Serializable
data class OperationDto(
    val id: Long,
    val categoryId: Long,
    val type: String,
    val amount: Long,
    val currency: String,
    val amountBase: Long,
    val rate: String,
    val date: String,
    val note: String?,
    val recurringId: Long?,
)

@Serializable
data class OperationInput(
    val categoryId: Long,
    val amount: Long,
    val currency: String,
    val date: String,
    val note: String? = null,
)

@Serializable
data class CategoryTotalDto(val categoryId: Long, val total: Long)

@Serializable
data class DayTotalDto(val date: String, val expense: Long, val income: Long)

@Serializable
data class BudgetDto(val categoryId: Long, val limit: Long, val spent: Long, val forecast: Long?)

@Serializable
data class OverviewDto(
    val month: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
    val previousExpense: Long,
    val expenseByCategory: List<CategoryTotalDto>,
    val days: List<DayTotalDto>,
    val budgets: List<BudgetDto>,
)

@Serializable
data class BudgetInput(val limit: Long)

@Serializable
data class CountDto(val count: Int)

/** kind: monthly (day 1..31) | weekly (weekday 1=Mon..7=Sun) | yearly (month 1..12, day). */
@Serializable
data class ScheduleDto(val kind: String, val day: Int? = null, val weekday: Int? = null, val month: Int? = null)

@Serializable
data class RecurringDto(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val amount: Long,
    val currency: String,
    val schedule: ScheduleDto,
    val description: String,
    val nextRun: String,
    val paused: Boolean,
)

@Serializable
data class RecurringInput(
    val name: String,
    val categoryId: Long,
    val amount: Long,
    val currency: String,
    val schedule: ScheduleDto,
    val paused: Boolean = false,
)

@Serializable
data class MonthReportDto(val month: String, val income: Long, val expense: Long, val expenseByCategory: List<CategoryTotalDto>)

@Serializable
data class ExportInput(val from: String, val to: String)

@Serializable
data class ErrorDto(val error: String)

@Serializable
data class CategoryDetailsDto(
    val id: Long,
    val name: String,
    val emoji: String,
    val type: String,
    val aliases: List<String>,
    val operations: Int,
    val total: Long,
    val recurring: Int,
)

@Serializable
data class CategoryCreateInput(val name: String, val type: String)

@Serializable
data class CategoryRenameInput(val name: String)

@Serializable
data class AliasInput(val word: String)

@Serializable
data class AliasDto(val alias: String)

/** [rate] is BYN per 1 unit; [unit] is how many units are usually quoted (100 for RUB). */
@Serializable
data class RateDto(val currency: String, val rate: String, val unit: Int)

@Serializable
data class RatesDto(val date: String, val rates: List<RateDto>)

fun Category.toDto(archived: Boolean = false) = CategoryDto(id, name, emoji, type.name, archived)

fun Operation.toDto() = OperationDto(
    id = id,
    categoryId = category.id,
    type = category.type.name,
    amount = amountMinor,
    currency = currency.code,
    amountBase = amountBaseMinor,
    rate = rate.toPlainString(),
    date = date.toString(),
    note = note,
    recurringId = recurringId,
)

fun Schedule.toDto(): ScheduleDto = when (this) {
    is Schedule.Monthly -> ScheduleDto("monthly", day = day)
    is Schedule.Weekly -> ScheduleDto("weekly", weekday = day.value)
    is Schedule.Yearly -> ScheduleDto("yearly", day = day, month = month)
}

fun ScheduleDto.toSchedule(): Schedule = when (kind) {
    "monthly" -> Schedule.Monthly(requireNotNull(day) { "day is required" })
    "weekly" -> Schedule.Weekly(java.time.DayOfWeek.of(requireNotNull(weekday) { "weekday is required" }))
    "yearly" -> Schedule.Yearly(requireNotNull(month) { "month is required" }, requireNotNull(day) { "day is required" })
    else -> throw IllegalArgumentException("Unknown schedule kind $kind")
}

fun Recurring.toDto() = RecurringDto(
    id = id,
    name = name,
    categoryId = category.id,
    amount = amountMinor,
    currency = currency.code,
    schedule = schedule.toDto(),
    description = schedule.describe(),
    nextRun = nextRun.toString(),
    paused = paused,
)
