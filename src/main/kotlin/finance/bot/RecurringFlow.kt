package finance.bot

import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.NewOperation
import finance.domain.Schedule
import finance.parse.EntryParser
import finance.parse.ParseResult
import finance.service.CategoryService
import finance.service.RecurringService
import finance.telegram.InlineKeyboardButton
import finance.telegram.TelegramApi
import java.time.LocalDate

/** /recurring and recurring payment buttons. */
class RecurringFlow(
    private val api: TelegramApi,
    private val recurring: RecurringService,
    private val categories: CategoryService,
    private val parser: EntryParser,
    private val today: () -> LocalDate,
) {
    private data class Draft(val entry: NewOperation, val name: String, val schedule: Schedule)

    /** Payments waiting for a category; kept in memory only. */
    private val drafts = mutableMapOf<Long, Draft>()
    private var nextDraftId = 1L

    suspend fun command(chatId: Long, args: String) {
        val sub = args.substringBefore(' ').lowercase()
        when (sub) {
            "" -> {
                val list = recurring.list()
                api.sendMessage(chatId, Texts.recurringList(list, today()), Keyboards.recurring(list))
            }
            "add" -> add(chatId, args.substringAfter(' ', "").trim())
            else -> api.sendMessage(chatId, Texts.RECURRING_HELP)
        }
    }

    private suspend fun add(chatId: Long, text: String) {
        var tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val schedule = tokens.lastOrNull()?.let(Schedule::parse)
        if (tokens.size < 3 || schedule == null) return api.sendMessage(chatId, Texts.RECURRING_HELP)
        // "+Зарплата 2000 5": a plus before the name marks income
        val incomeName = tokens[0].length > 1 && tokens[0].startsWith("+") && tokens[0][1].isLetter()
        if (incomeName) tokens = listOf(tokens[0].drop(1)) + tokens.drop(1)
        val parsed = (parser.parse(tokens.dropLast(1).joinToString(" "), today()) as? ParseResult.Ok)?.entry
        val entry = if (incomeName) parsed?.copy(type = CategoryType.INCOME) else parsed
        val name = entry?.note ?: return api.sendMessage(chatId, Texts.RECURRING_HELP)

        val category = categories.match(name, entry.type)
        if (category != null) return api.sendMessage(chatId, create(Draft(entry, name, schedule), category))

        val draftId = nextDraftId++
        drafts[draftId] = Draft(entry, name, schedule)
        api.sendMessage(
            chatId,
            Texts.recurringCategory(name),
            Keyboards.categories(categories.active(entry.type), { "rc:$draftId:${it.id}" }, listOf(InlineKeyboardButton("✖️ Отмена", "rx:$draftId"))),
        )
    }

    private fun create(draft: Draft, category: Category): String {
        val payment = recurring.create(draft.name, category, draft.entry.amountMinor, draft.entry.currency, draft.schedule, today())
        return Texts.recurringCreated(payment, today())
    }

    suspend fun onCallback(action: String, ctx: CallbackContext): Boolean {
        when (action) {
            "rc" -> {
                val draft = drafts[ctx.first]
                val category = ctx.second?.let { categories.findById(it) }
                if (draft == null || category == null) {
                    api.editMessageText(ctx.chatId, ctx.messageId, "Черновик устарел, добавь платёж заново")
                    return true
                }
                drafts.remove(ctx.first)
                api.editMessageText(ctx.chatId, ctx.messageId, create(draft, category))
            }
            "rx" -> {
                drafts.remove(ctx.first)
                api.editMessageText(ctx.chatId, ctx.messageId, "✖️ Не добавлено")
            }
            "rd" -> {
                ctx.notice = if (recurring.delete(ctx.first)) "Удалено" else "Уже удалено"
                val list = recurring.list()
                api.editMessageText(ctx.chatId, ctx.messageId, Texts.recurringList(list, today()), Keyboards.recurring(list))
            }
            else -> return false
        }
        return true
    }
}
