package finance.bot

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.Money
import finance.domain.NewOperation
import finance.domain.Operation
import finance.service.Summary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object Texts {
    val MONTHS = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM")
    private val FULL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val BASE = Currency.BASE.code

    val HELP = """
        Привет! Я записываю доходы и расходы.

        Пиши одной строкой:
        • кофе 5 — расход 5 BYN
        • такси 20 usd — расход в долларах, пересчитаю в BYN по курсу
        • продукты 45,5 вчера — с датой (вчера, позавчера, 05.11)
        • +1500 зп — доход

        Валюты: BYN (по умолчанию), USD ($), EUR (€), RUB (₽).

        Команды:
        /today — сегодня
        /week — эта неделя
        /month — месяц (/month октябрь, /month 10.2026)
        /last — последние 10 операций
        /undo — удалить последнюю
        /rate — курсы валют
    """.trimIndent()

    fun date(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> date.format(if (date.year == today.year) DAY_MONTH else FULL_DATE)
    }

    private fun sign(type: CategoryType) = if (type == CategoryType.INCOME) "+" else "−"

    fun amount(type: CategoryType, amountMinor: Long, currency: Currency) =
        "${sign(type)}${Money.format(amountMinor)} ${currency.code}"

    fun amount(op: Operation, withRate: Boolean = false): String {
        val main = amount(op.category.type, op.amountMinor, op.currency)
        if (op.currency == Currency.BASE) return main
        val rate = if (withRate) ", курс ${rate(op.rate, op.currency)}" else ""
        return "$main (≈ ${Money.format(op.amountBaseMinor)} $BASE$rate)"
    }

    private fun rate(rate: BigDecimal, currency: Currency): String {
        val value = rate.multiply(BigDecimal(currency.rateUnit)).setScale(4, RoundingMode.HALF_UP).toPlainString()
        return if (currency.rateUnit == 1) value else "$value за ${currency.rateUnit}"
    }

    fun saved(op: Operation, today: LocalDate): String = buildString {
        appendLine("${op.category.label}  ${amount(op, withRate = true)}")
        append("📅 ${date(op.date, today)}")
        op.note?.let { append(" · $it") }
    }

    fun line(op: Operation, today: LocalDate, withDate: Boolean): String = buildString {
        if (withDate) append("${date(op.date, today)} ")
        append("${op.category.label} ${amount(op)}")
        op.note?.let { append(" · $it") }
    }

    fun needCategory(entry: NewOperation): String {
        val amount = amount(entry.type, entry.amountMinor, entry.currency)
        return if (entry.note == null) "Куда записать $amount?" else "Не знаю категорию для «${entry.note}» — $amount. Куда записать?"
    }

    fun learned(op: Operation, word: String?) =
        if (word == null) "" else "\n🧠 Запомнил: «$word» → ${op.category.label}"

    fun day(summary: Summary, today: LocalDate): String {
        if (summary.operations.isEmpty()) return "Сегодня операций нет"
        return buildString {
            appendLine("📅 Сегодня, ${today.format(DAY_MONTH)}")
            summary.operations.forEach { appendLine(line(it, today, withDate = false)) }
            appendLine()
            append("Расходы: ${Money.format(summary.expenseMinor)} $BASE")
            if (summary.incomeMinor > 0) append("\nДоходы: ${Money.format(summary.incomeMinor)} $BASE")
        }
    }

    fun monthTitle(month: YearMonth) = "📊 ${MONTHS[month.monthValue - 1]} ${month.year}"

    fun weekTitle(from: LocalDate, to: LocalDate) = "📊 Неделя ${from.format(DAY_MONTH)}–${to.format(DAY_MONTH)}"

    fun summary(title: String, summary: Summary): String = buildString {
        appendLine(title)
        if (summary.operations.isEmpty()) {
            append("Операций нет")
            return@buildString
        }
        val balanceSign = if (summary.balanceMinor >= 0) "+" else "−"
        appendLine("Доходы:  +${Money.format(summary.incomeMinor)} $BASE")
        appendLine("Расходы: −${Money.format(summary.expenseMinor)} $BASE")
        append("Баланс:  $balanceSign${Money.format(summary.balanceMinor)} $BASE")
        if (summary.expenseByCategory.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Расходы по категориям:")
            summary.expenseByCategory.forEach { total ->
                val share = (total.totalMinor * 100.0 / summary.expenseMinor).toInt()
                appendLine("${total.category.label}  ${Money.format(total.totalMinor)}  ${bar(share)} $share%")
            }
        }
    }.trimEnd()

    private fun bar(percent: Int): String {
        val filled = (percent + 5) / 10
        return "█".repeat(filled.coerceIn(0, 10)) + "░".repeat((10 - filled).coerceIn(0, 10))
    }

    fun last(ops: List<Operation>, today: LocalDate): String =
        if (ops.isEmpty()) {
            "Операций пока нет"
        } else {
            "Последние операции:\n" + ops.mapIndexed { i, op -> "${i + 1}. ${line(op, today, withDate = true)}" }
                .joinToString("\n")
        }

    fun rates(date: LocalDate, rates: Map<Currency, BigDecimal>): String =
        if (rates.isEmpty()) {
            "Не удалось получить курсы, попробуй позже"
        } else {
            "💱 Курсы на ${date.format(DAY_MONTH)} (к $BASE):\n" +
                rates.entries.joinToString("\n") { (currency, rate) ->
                    val unit = if (currency.rateUnit == 1) currency.code else "${currency.rateUnit} ${currency.code}"
                    "$unit  ${rate.multiply(BigDecimal(currency.rateUnit)).setScale(4, RoundingMode.HALF_UP).toPlainString()}"
                }
        }

    fun rateUnavailable(currency: Currency, date: LocalDate) =
        "Не удалось получить курс ${currency.code} на ${date.format(FULL_DATE)}, попробуй позже"
}
