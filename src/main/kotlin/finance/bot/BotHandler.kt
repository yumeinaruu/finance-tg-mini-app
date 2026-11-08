package finance.bot

import finance.domain.Currency
import finance.domain.Operation
import finance.parse.EntryParser
import finance.parse.ParseResult
import finance.rates.RateService
import finance.rates.RateUnavailableException
import finance.service.BudgetService
import finance.service.CategoryMatcher
import finance.service.EntryService
import finance.service.RecordResult
import finance.service.ReportService
import finance.telegram.CallbackQuery
import finance.telegram.InlineKeyboardButton
import finance.telegram.Message
import finance.telegram.TelegramApi
import java.time.DayOfWeek
import java.time.LocalDate

/** Routes messages and button presses; category, budget and recurring features live in their flows. */
class BotHandler(
    private val api: TelegramApi,
    private val parser: EntryParser,
    private val entries: EntryService,
    private val reports: ReportService,
    private val rates: RateService,
    private val budgets: BudgetService,
    private val categoryFlow: CategoryFlow,
    private val budgetFlow: BudgetFlow,
    private val recurringFlow: RecurringFlow,
    private val conversation: Conversation,
    private val today: () -> LocalDate,
) {
    suspend fun onMessage(message: Message) {
        val text = message.text?.trim().orEmpty()
        if (text.isEmpty()) return
        val chatId = message.chat.id
        if (text.startsWith("/")) {
            val awaiting = conversation.awaiting
            conversation.awaiting = null
            return onCommand(chatId, text, wasAwaiting = awaiting != null)
        }
        when (val awaiting = conversation.awaiting) {
            null -> onEntry(chatId, text)
            is Awaiting.CategoryName -> {
                conversation.awaiting = null
                categoryFlow.add(chatId, text, awaiting.pendingId, awaiting.type)
            }
            is Awaiting.Amount -> onNewAmount(chatId, text, awaiting.operationId)
        }
    }

    private suspend fun onCommand(chatId: Long, text: String, wasAwaiting: Boolean) {
        val command = text.substringBefore(' ').substringBefore('@').lowercase()
        val args = text.substringAfter(' ', "").trim()
        val today = today()
        when (command) {
            "/start", "/help" -> api.sendMessage(chatId, Texts.HELP)
            "/cancel" -> api.sendMessage(chatId, if (wasAwaiting) "Ок, отменил" else "Нечего отменять")
            "/today" -> api.sendMessage(chatId, Texts.day(reports.summary(today, today), today))
            "/week" -> {
                val from = today.with(DayOfWeek.MONDAY)
                api.sendMessage(chatId, Texts.summary(Texts.weekTitle(from, today), reports.summary(from, today)))
            }
            "/month" -> {
                val month = MonthArg.parse(args, today)
                    ?: return api.sendMessage(chatId, "Не понял месяц. Примеры: /month, /month октябрь, /month 10.2026")
                val summary = reports.summary(month.atDay(1), month.atEndOfMonth())
                api.sendMessage(chatId, Texts.monthReport(month, summary, budgets.progress(month)))
            }
            "/last" -> {
                val ops = entries.last(LAST_LIMIT)
                api.sendMessage(chatId, Texts.last(ops, today), Keyboards.last(ops))
            }
            "/undo" -> {
                val op = entries.undo()
                api.sendMessage(chatId, op?.let { "↩️ Удалил: ${Texts.line(it, today, withDate = true)}" } ?: "Нечего отменять")
            }
            "/rate" -> {
                val filter = args.takeIf { it.isNotEmpty() }?.let {
                    Currency.fromAlias(it) ?: return api.sendMessage(chatId, "Не знаю валюту «$it»")
                }
                val all = rates.rates(today)
                api.sendMessage(chatId, Texts.rates(today, if (filter == null) all else all.filterKeys { it == filter }))
            }
            "/categories" -> categoryFlow.showAll(chatId)
            "/cat" -> categoryFlow.cat(chatId, args)
            "/alias" -> categoryFlow.alias(chatId, args)
            "/budget" -> budgetFlow.command(chatId, args)
            "/recurring" -> recurringFlow.command(chatId, args)
            else -> api.sendMessage(chatId, "Не знаю такую команду. /help — список команд")
        }
    }

    private suspend fun onEntry(chatId: Long, text: String) {
        val today = today()
        val entry = when (val parsed = parser.parse(text, today)) {
            is ParseResult.Error -> return api.sendMessage(chatId, parsed.message)
            is ParseResult.Ok -> parsed.entry
        }
        val result = try {
            entries.record(entry)
        } catch (e: RateUnavailableException) {
            return api.sendMessage(chatId, Texts.rateUnavailable(e.currency, e.date))
        }
        when (result) {
            is RecordResult.Saved -> api.sendMessage(chatId, savedText(result.operation), Keyboards.operation(result.operation))
            is RecordResult.NeedCategory -> api.sendMessage(
                chatId,
                Texts.needCategory(result.entry),
                Keyboards.categories(result.options, { "pc:${result.pendingId}:${it.id}" }, Keyboards.newCategoryOrCancel(result.pendingId)),
            )
        }
    }

    private suspend fun onNewAmount(chatId: Long, text: String, operationId: Long) {
        val amount = EntryParser.parseAmount(text)
            ?: return api.sendMessage(chatId, "Это не похоже на сумму. Напиши число, например 12.50, или /cancel")
        conversation.awaiting = null
        val op = entries.updateAmount(operationId, amount) ?: return api.sendMessage(chatId, "Операция уже удалена")
        api.sendMessage(chatId, "✏️ Сумма изменена\n" + savedText(op), Keyboards.operation(op))
    }

    /** Saved operation text with a budget warning when a threshold was crossed. */
    private fun savedText(op: Operation, suffix: String = ""): String =
        Texts.withAlert(Texts.saved(op, today()) + suffix, budgets.alertFor(op, today()))

    suspend fun onCallback(callback: CallbackQuery) {
        val message = callback.message
        val parts = callback.data.orEmpty().split(':')
        val ids = parts.drop(1).map { it.toLongOrNull() ?: return api.answerCallbackQuery(callback.id) }
        if (message == null || ids.isEmpty()) return api.answerCallbackQuery(callback.id)

        val ctx = CallbackContext(message.chat.id, message.messageId, ids)
        val action = parts[0]
        val handled = onCoreCallback(action, ctx) ||
            categoryFlow.onCallback(action, ctx) ||
            budgetFlow.onCallback(action, ctx) ||
            recurringFlow.onCallback(action, ctx)
        api.answerCallbackQuery(callback.id, if (handled) ctx.notice else null)
    }

    private suspend fun onCoreCallback(action: String, ctx: CallbackContext): Boolean {
        val today = today()
        when (action) {
            "pc" -> {
                val op = try {
                    ctx.second?.let { entries.completePending(ctx.first, it) }
                } catch (e: RateUnavailableException) {
                    ctx.notice = Texts.rateUnavailable(e.currency, e.date)
                    return true
                }
                if (op == null) {
                    api.editMessageText(ctx.chatId, ctx.messageId, "Эта запись уже обработана")
                } else {
                    val word = CategoryMatcher.words(op.note).firstOrNull { it.length >= 2 }
                    api.editMessageText(ctx.chatId, ctx.messageId, savedText(op, Texts.learned(op, word)), Keyboards.operation(op))
                }
            }
            "px" -> {
                entries.cancelPending(ctx.first)
                api.editMessageText(ctx.chatId, ctx.messageId, "✖️ Не записано")
            }
            "cc" -> {
                val op = entries.find(ctx.first) ?: return deleted(ctx)
                api.editMessageReplyMarkup(
                    ctx.chatId,
                    ctx.messageId,
                    Keyboards.categories(
                        entries.categoriesFor(op.category.type),
                        { "sc:${op.id}:${it.id}" },
                        listOf(InlineKeyboardButton("← Назад", "cb:${op.id}")),
                    ),
                )
            }
            "sc" -> {
                val op = ctx.second?.let { entries.recategorize(ctx.first, it) } ?: return deleted(ctx)
                api.editMessageText(ctx.chatId, ctx.messageId, savedText(op), Keyboards.operation(op))
                ctx.notice = "Категория: ${op.category.label}"
            }
            "cb" -> {
                val op = entries.find(ctx.first) ?: return deleted(ctx)
                api.editMessageReplyMarkup(ctx.chatId, ctx.messageId, Keyboards.operation(op))
            }
            "d" -> {
                val op = entries.delete(ctx.first) ?: return deleted(ctx)
                api.editMessageText(ctx.chatId, ctx.messageId, "🗑 Удалено: ${Texts.line(op, today, withDate = true)}")
            }
            "ld" -> {
                ctx.notice = if (entries.delete(ctx.first) != null) "Удалено" else "Уже удалено"
                val ops = entries.last(LAST_LIMIT)
                api.editMessageText(ctx.chatId, ctx.messageId, Texts.last(ops, today), Keyboards.last(ops))
            }
            "ea" -> {
                val op = entries.find(ctx.first) ?: return deleted(ctx)
                conversation.awaiting = Awaiting.Amount(op.id)
                api.sendMessage(ctx.chatId, "Напиши новую сумму для «${op.note ?: op.category.name}» в ${op.currency.code}.\n/cancel — отмена")
            }
            else -> return false
        }
        return true
    }

    private fun deleted(ctx: CallbackContext): Boolean {
        ctx.notice = "Операция уже удалена"
        return true
    }

    private companion object {
        const val LAST_LIMIT = 10
    }
}
