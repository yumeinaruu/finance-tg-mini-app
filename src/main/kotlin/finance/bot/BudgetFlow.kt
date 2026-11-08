package finance.bot

import finance.domain.CategoryType
import finance.parse.EntryParser
import finance.service.BudgetService
import finance.service.CategoryService
import finance.telegram.TelegramApi
import java.time.LocalDate
import java.time.YearMonth

/** /budget and budget buttons. */
class BudgetFlow(
    private val api: TelegramApi,
    private val budgets: BudgetService,
    private val categories: CategoryService,
    private val today: () -> LocalDate,
) {
    suspend fun command(chatId: Long, args: String) {
        val month = YearMonth.from(today())
        val tokens = args.split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            tokens.isEmpty() -> api.sendMessage(chatId, Texts.budgets(month, budgets.progress(month)))
            tokens.size == 1 && tokens[0].equals("copy", ignoreCase = true) -> {
                val copied = budgets.copy(month.minusMonths(1), month)
                api.sendMessage(chatId, if (copied > 0) "📋 Скопировал с прошлого месяца: $copied" else "Нечего копировать")
            }
            tokens.size < 2 -> api.sendMessage(chatId, Texts.BUDGET_HELP)
            else -> set(chatId, month, query = tokens.dropLast(1).joinToString(" "), amount = tokens.last())
        }
    }

    private suspend fun set(chatId: Long, month: YearMonth, query: String, amount: String) {
        val category = categories.find(query, CategoryType.EXPENSE)
            ?: return api.sendMessage(chatId, "Не нашёл категорию расходов «$query». Список: /categories")
        if (amount == "0" || amount == "-") {
            val removed = budgets.remove(category, month)
            return api.sendMessage(chatId, if (removed) "Убрал бюджет для ${category.label}" else "Бюджета для ${category.label} не было")
        }
        val limit = EntryParser.parseAmount(amount) ?: return api.sendMessage(chatId, Texts.BUDGET_HELP)
        budgets.set(category, month, limit)
        api.sendMessage(chatId, Texts.budgetSet(month, budgets.progress(category, month)!!))
    }

    suspend fun onCallback(action: String, ctx: CallbackContext): Boolean {
        when (action) {
            "bc" -> {
                val from = YearMonth.of((ctx.first / 100).toInt(), (ctx.first % 100).toInt())
                val copied = budgets.copy(from, from.plusMonths(1))
                api.editMessageReplyMarkup(ctx.chatId, ctx.messageId, null)
                ctx.notice = "Скопировано бюджетов: $copied"
            }
            "bn" -> api.editMessageReplyMarkup(ctx.chatId, ctx.messageId, null)
            else -> return false
        }
        return true
    }
}
