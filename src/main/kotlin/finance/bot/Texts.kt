package finance.bot

import finance.domain.BudgetAlert
import finance.domain.BudgetProgress
import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.Money
import finance.domain.NewOperation
import finance.domain.Operation
import finance.domain.Recurring
import finance.service.CategoryUsage
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
        /budget — бюджеты (/budget еда 600)
        /recurring — регулярные платежи
        /categories — категории и слова для них
        /rate — курсы валют
        /cancel — отменить текущий ввод
    """.trimIndent()

    val CAT_HELP = """
        Управление категориями:
        /cat add Спорт 🏋️ — новая категория расходов
        /cat add +Кэшбэк 💸 — новая категория доходов
        /cat rename Спорт Фитнес — переименовать (можно сменить emoji)
        /cat del Спорт — удалить
        /alias бассейн здоровье — привязать слово к категории
        /alias del бассейн — отвязать слово
    """.trimIndent()

    val BUDGET_HELP = """
        Бюджеты на текущий месяц:
        /budget — прогресс по бюджетам
        /budget еда 600 — задать лимит
        /budget еда 0 — убрать лимит
        /budget copy — скопировать бюджеты с прошлого месяца
    """.trimIndent()

    val RECURRING_HELP = """
        Формат: /recurring add <название> <сумма> [валюта] <когда>
        Когда: 15 — каждый месяц 15-го, пн — каждую неделю, 01.03 — каждый год
        Примеры:
        /recurring add Netflix 10 usd 15
        /recurring add Аренда 450 1
        /recurring add +Зарплата 2000 5
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

    fun monthName(month: YearMonth) = "${MONTHS[month.monthValue - 1]} ${month.year}"

    fun monthTitle(month: YearMonth) = "📊 ${monthName(month)}"

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

    fun monthReport(month: YearMonth, summary: Summary, budgets: List<BudgetProgress>): String =
        summary(monthTitle(month), summary) + if (budgets.isEmpty()) "" else "\n\n" + budgets(month, budgets)

    private fun budgetLine(p: BudgetProgress): String {
        val mark = when {
            p.percent >= 100 -> " 🚨"
            p.percent >= 80 -> " ⚠️"
            else -> ""
        }
        return "${p.category.label}  ${Money.format(p.spentMinor)} / ${Money.format(p.limitMinor)}  ${bar(minOf(p.percent, 100))} ${p.percent}%$mark"
    }

    fun budgets(month: YearMonth, progress: List<BudgetProgress>): String {
        if (progress.isEmpty()) return "На ${monthName(month)} бюджетов нет.\nЗадай лимит: /budget еда 600"
        return buildString {
            appendLine("💰 Бюджеты на ${monthName(month)}, $BASE:")
            progress.forEach { appendLine(budgetLine(it)) }
            append("Итого: ${Money.format(progress.sumOf { it.spentMinor })} / ${Money.format(progress.sumOf { it.limitMinor })}")
        }
    }

    fun budgetSet(month: YearMonth, p: BudgetProgress) =
        "✅ Бюджет на ${monthName(month)}: ${p.category.label} — ${Money.format(p.limitMinor)} $BASE\n" +
            "Уже потрачено: ${Money.format(p.spentMinor)} (${p.percent}%)"

    fun alert(alert: BudgetAlert): String {
        val p = alert.progress
        val label = p.category.label
        if (alert.threshold >= 100) {
            return "🚨 $label: бюджет превышен — ${Money.format(p.spentMinor)} / ${Money.format(p.limitMinor)} $BASE"
        }
        val left = alert.daysLeft?.let { ", осталось ${Money.format(p.limitMinor - p.spentMinor)} $BASE на $it дн." }.orEmpty()
        return "⚠️ $label: ${Money.format(p.spentMinor)} / ${Money.format(p.limitMinor)} $BASE (${p.percent}%)$left"
    }

    fun withAlert(text: String, alert: BudgetAlert?) = if (alert == null) text else "$text\n\n${alert(alert)}"

    fun categories(overview: List<Pair<Category, List<String>>>): String = buildString {
        appendLine("📂 Категории")
        CategoryType.entries.forEach { type ->
            val items = overview.filter { it.first.type == type }
            if (items.isEmpty()) return@forEach
            appendLine()
            appendLine(if (type == CategoryType.EXPENSE) "Расходы:" else "Доходы:")
            items.forEach { (category, aliases) ->
                append(category.label)
                if (aliases.isNotEmpty()) {
                    append(" — ")
                    append(aliases.take(MAX_ALIASES_SHOWN).joinToString(", "))
                    if (aliases.size > MAX_ALIASES_SHOWN) append(", …")
                }
                appendLine()
            }
        }
        appendLine()
        append(CAT_HELP)
    }

    fun categoryInput(type: CategoryType) =
        "Напиши название новой категории ${if (type == CategoryType.INCOME) "доходов" else "расходов"}, " +
            "можно с emoji, например «Спорт 🏋️».\n/cancel — отмена"

    fun deleteQuestion(category: Category, usage: CategoryUsage): String {
        val parts = buildList {
            if (usage.operations > 0) {
                add("${usage.operations} ${plural(usage.operations, "операция", "операции", "операций")} на ${Money.format(usage.totalMinor)} $BASE")
            }
            if (usage.recurring > 0) {
                add("${usage.recurring} ${plural(usage.recurring, "регулярный платёж", "регулярных платежа", "регулярных платежей")}")
            }
        }
        return "В «${category.label}» ${parts.joinToString(" и ")}. Что с ними сделать?"
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    private fun recurringBase(r: Recurring) =
        "${r.name} — ${Money.format(r.amountMinor)} ${r.currency.code} · ${r.category.label} · ${r.schedule.describe()}"

    fun recurringList(list: List<Recurring>, today: LocalDate): String =
        if (list.isEmpty()) {
            "Регулярных платежей нет.\n\n$RECURRING_HELP"
        } else {
            "🔁 Регулярные платежи:\n" + list.mapIndexed { i, r ->
                "${i + 1}. ${recurringBase(r)} · след. ${date(r.nextRun, today)}"
            }.joinToString("\n")
        }

    fun recurringCreated(r: Recurring, today: LocalDate) =
        "🔁 Добавил регулярный платёж:\n${recurringBase(r)}\nПервое списание: ${date(r.nextRun, today)}"

    fun recurringCategory(name: String) = "Какую категорию поставить для «$name»?"

    fun recurringAdded(op: Operation, today: LocalDate): String {
        val date = if (op.date == today) "" else " · 📅 ${date(op.date, today)}"
        return "🔁 Добавил: ${op.note} — ${amount(op)}, ${op.category.label}$date"
    }

    private const val MAX_ALIASES_SHOWN = 8
}
