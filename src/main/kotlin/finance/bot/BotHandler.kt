package finance.bot

import finance.domain.Currency
import finance.parse.EntryParser
import finance.parse.ParseResult
import finance.rates.RateService
import finance.rates.RateUnavailableException
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

class BotHandler(
    private val api: TelegramApi,
    private val parser: EntryParser,
    private val entries: EntryService,
    private val reports: ReportService,
    private val rates: RateService,
    private val today: () -> LocalDate,
) {

    suspend fun onMessage(message: Message) {
        val text = message.text?.trim().orEmpty()
        if (text.isEmpty()) return
        if (text.startsWith("/")) onCommand(message.chat.id, text) else onEntry(message.chat.id, text)
    }

    private suspend fun onCommand(chatId: Long, text: String) {
        val command = text.substringBefore(' ').substringBefore('@').lowercase()
        val args = text.substringAfter(' ', "").trim()
        val today = today()
        when (command) {
            "/start", "/help" -> api.sendMessage(chatId, Texts.HELP)
            "/today" -> api.sendMessage(chatId, Texts.day(reports.summary(today, today), today))
            "/week" -> {
                val from = today.with(DayOfWeek.MONDAY)
                api.sendMessage(chatId, Texts.summary(Texts.weekTitle(from, today), reports.summary(from, today)))
            }
            "/month" -> {
                val month = MonthArg.parse(args, today)
                    ?: return api.sendMessage(chatId, "Не понял месяц. Примеры: /month, /month октябрь, /month 10.2026")
                api.sendMessage(chatId, Texts.summary(Texts.monthTitle(month), reports.summary(month.atDay(1), month.atEndOfMonth())))
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
            is RecordResult.Saved -> api.sendMessage(chatId, Texts.saved(result.operation, today), Keyboards.operation(result.operation))
            is RecordResult.NeedCategory -> api.sendMessage(
                chatId,
                Texts.needCategory(result.entry),
                Keyboards.categories(
                    result.options,
                    data = { "pc:${result.pendingId}:${it.id}" },
                    extra = InlineKeyboardButton("✖️ Отмена", "px:${result.pendingId}"),
                ),
            )
        }
    }

    suspend fun onCallback(callback: CallbackQuery) {
        val message = callback.message
        val parts = callback.data.orEmpty().split(':')
        val first = parts.getOrNull(1)?.toLongOrNull()
        val second = parts.getOrNull(2)?.toLongOrNull()
        if (message == null || first == null) return api.answerCallbackQuery(callback.id)

        val chatId = message.chat.id
        val messageId = message.messageId
        val today = today()
        var notice: String? = null

        when (parts[0]) {
            "pc" -> {
                val op = try {
                    second?.let { entries.completePending(first, it) }
                } catch (e: RateUnavailableException) {
                    return api.answerCallbackQuery(callback.id, Texts.rateUnavailable(e.currency, e.date))
                }
                if (op == null) {
                    api.editMessageText(chatId, messageId, "Эта запись уже обработана")
                } else {
                    val word = CategoryMatcher.words(op.note).firstOrNull { it.length >= 2 }
                    api.editMessageText(chatId, messageId, Texts.saved(op, today) + Texts.learned(op, word), Keyboards.operation(op))
                }
            }
            "px" -> {
                entries.cancelPending(first)
                api.editMessageText(chatId, messageId, "✖️ Не записано")
            }
            "cc" -> {
                val op = entries.find(first) ?: return answerDeleted(callback.id)
                api.editMessageReplyMarkup(
                    chatId,
                    messageId,
                    Keyboards.categories(
                        entries.categoriesFor(op.category.type),
                        data = { "sc:${op.id}:${it.id}" },
                        extra = InlineKeyboardButton("← Назад", "cb:${op.id}"),
                    ),
                )
            }
            "sc" -> {
                val op = second?.let { entries.recategorize(first, it) } ?: return answerDeleted(callback.id)
                api.editMessageText(chatId, messageId, Texts.saved(op, today), Keyboards.operation(op))
                notice = "Категория: ${op.category.label}"
            }
            "cb" -> {
                val op = entries.find(first) ?: return answerDeleted(callback.id)
                api.editMessageReplyMarkup(chatId, messageId, Keyboards.operation(op))
            }
            "d" -> {
                val op = entries.delete(first) ?: return answerDeleted(callback.id)
                api.editMessageText(chatId, messageId, "🗑 Удалено: ${Texts.line(op, today, withDate = true)}")
            }
            "ld" -> {
                notice = if (entries.delete(first) != null) "Удалено" else "Уже удалено"
                val ops = entries.last(LAST_LIMIT)
                api.editMessageText(chatId, messageId, Texts.last(ops, today), Keyboards.last(ops))
            }
        }
        api.answerCallbackQuery(callback.id, notice)
    }

    private suspend fun answerDeleted(callbackId: String) = api.answerCallbackQuery(callbackId, "Операция уже удалена")

    private companion object {
        const val LAST_LIMIT = 10
    }
}
