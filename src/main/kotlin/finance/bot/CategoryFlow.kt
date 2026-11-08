package finance.bot

import finance.domain.Category
import finance.domain.CategoryType
import finance.rates.RateUnavailableException
import finance.service.AddCategoryResult
import finance.service.CategoryInput
import finance.service.CategoryService
import finance.service.EntryService
import finance.telegram.InlineKeyboardButton
import finance.telegram.InlineKeyboardMarkup
import finance.telegram.TelegramApi
import java.time.LocalDate

/** /categories, /cat, /alias and category buttons. */
class CategoryFlow(
    private val api: TelegramApi,
    private val categories: CategoryService,
    private val entries: EntryService,
    private val conversation: Conversation,
    private val today: () -> LocalDate,
) {
    suspend fun showAll(chatId: Long) =
        api.sendMessage(chatId, Texts.categories(categories.overview()), Keyboards.categoriesMenu())

    suspend fun cat(chatId: Long, args: String) {
        val sub = args.substringBefore(' ').lowercase()
        val rest = args.substringAfter(' ', "").trim()
        when (sub) {
            "" -> showAll(chatId)
            "add" -> add(chatId, rest, pendingId = null, defaultType = CategoryType.EXPENSE)
            "rename" -> rename(chatId, rest)
            "del", "delete" -> {
                val category = categories.find(rest) ?: return notFound(chatId, rest)
                askDelete(category, chatId, messageId = null)
            }
            else -> api.sendMessage(chatId, Texts.CAT_HELP)
        }
    }

    suspend fun alias(chatId: Long, args: String) {
        val tokens = args.split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            tokens.isEmpty() -> api.sendMessage(chatId, Texts.categories(categories.overview()))
            tokens[0].lowercase() == "del" && tokens.size == 2 -> {
                val removed = categories.removeAlias(tokens[1])
                api.sendMessage(chatId, if (removed) "Отвязал слово «${tokens[1]}»" else "Слово «${tokens[1]}» ни к чему не привязано")
            }
            tokens.size < 2 -> api.sendMessage(chatId, Texts.CAT_HELP)
            else -> {
                val query = tokens.drop(1).joinToString(" ")
                val category = categories.find(query) ?: return notFound(chatId, query)
                val word = categories.setAlias(tokens[0], category)
                    ?: return api.sendMessage(chatId, "В слове должны быть буквы или цифры")
                api.sendMessage(chatId, "🔗 «$word» → ${category.label}")
            }
        }
    }

    /** Creates a category from [text]; when [pendingId] is set, saves that operation into it. */
    suspend fun add(chatId: Long, text: String, pendingId: Long?, defaultType: CategoryType) {
        val parsed = CategoryInput.parse(text, defaultType)?.takeIf { it.name.isNotEmpty() }
            ?: return api.sendMessage(chatId, if (pendingId == null) Texts.CAT_HELP else "Не понял название. Например: «Спорт 🏋️»")
        if (parsed.name.length > CategoryInput.MAX_NAME_LENGTH) {
            return api.sendMessage(chatId, "Слишком длинное название, максимум ${CategoryInput.MAX_NAME_LENGTH} символа")
        }
        val input = if (pendingId != null) parsed.copy(type = defaultType) else parsed
        val result = categories.add(input)
        val category = result.category
        val kind = if (category.type == CategoryType.INCOME) "доходов" else "расходов"
        val message = when (result) {
            is AddCategoryResult.Created -> "✅ Добавил категорию $kind «${category.label}»"
            is AddCategoryResult.Restored -> "♻️ Вернул из архива категорию «${category.label}»"
            is AddCategoryResult.Exists -> "Категория «${category.label}» уже есть"
        }
        if (pendingId == null) return api.sendMessage(chatId, message)

        val operation = try {
            entries.completePending(pendingId, category.id)
        } catch (e: RateUnavailableException) {
            return api.sendMessage(chatId, Texts.rateUnavailable(e.currency, e.date))
        } ?: return api.sendMessage(chatId, "$message\nЗапись уже обработана")
        api.sendMessage(chatId, "$message\n\n${Texts.saved(operation, today())}", Keyboards.operation(operation))
    }

    private suspend fun rename(chatId: Long, args: String) {
        val tokens = args.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (split in tokens.size - 1 downTo 1) {
            val category = categories.find(tokens.take(split).joinToString(" ")) ?: continue
            val input = CategoryInput.parse(tokens.drop(split).joinToString(" "), category.type)
                ?: return api.sendMessage(chatId, Texts.CAT_HELP)
            val renamed = categories.rename(category, input.name, input.emoji)
                ?: return api.sendMessage(chatId, "Категория «${input.name}» уже есть")
            return api.sendMessage(chatId, "✏️ «${category.label}» → «${renamed.label}»")
        }
        api.sendMessage(chatId, "Не нашёл, что переименовать. Пример: /cat rename Спорт Фитнес 🏃")
    }

    private suspend fun askDelete(category: Category, chatId: Long, messageId: Long?) {
        val usage = categories.usage(category.id)
        if (usage.isEmpty) {
            categories.delete(category.id)
            reply(chatId, messageId, "🗑 Удалил категорию «${category.label}»")
        } else {
            reply(chatId, messageId, Texts.deleteQuestion(category, usage), Keyboards.deleteCategory(category.id))
        }
    }

    private suspend fun reply(chatId: Long, messageId: Long?, text: String, markup: InlineKeyboardMarkup? = null) =
        if (messageId == null) api.sendMessage(chatId, text, markup) else api.editMessageText(chatId, messageId, text, markup)

    private suspend fun notFound(chatId: Long, query: String) =
        api.sendMessage(chatId, "Не нашёл категорию «$query». Список: /categories")

    /** Returns false when [action] is not a category button. */
    suspend fun onCallback(action: String, ctx: CallbackContext): Boolean {
        when (action) {
            "ca" -> {
                val type = if (ctx.first == 1L) CategoryType.INCOME else CategoryType.EXPENSE
                conversation.awaiting = Awaiting.CategoryName(type, pendingId = null)
                api.sendMessage(ctx.chatId, Texts.categoryInput(type))
            }
            "pn" -> {
                val entry = entries.pending(ctx.first)
                    ?: return true.also { api.editMessageText(ctx.chatId, ctx.messageId, "Эта запись уже обработана") }
                conversation.awaiting = Awaiting.CategoryName(entry.type, pendingId = ctx.first)
                api.editMessageText(ctx.chatId, ctx.messageId, Texts.needCategory(entry) + "\n\n" + Texts.categoryInput(entry.type))
            }
            "cd" -> api.sendMessage(
                ctx.chatId,
                "Какую категорию удалить?",
                Keyboards.categories(categories.overview().map { it.first }, { "cx:${it.id}" }, listOf(InlineKeyboardButton("✖️ Отмена", "cn:0"))),
            )
            "cx" -> {
                val category = categories.findById(ctx.first) ?: return true.also { ctx.notice = "Категория уже удалена" }
                askDelete(category, ctx.chatId, ctx.messageId)
            }
            "cm" -> {
                val category = categories.findById(ctx.first) ?: return true.also { ctx.notice = "Категория уже удалена" }
                val targets = categories.active(category.type).filter { it.id != category.id }
                api.editMessageText(
                    ctx.chatId,
                    ctx.messageId,
                    "Куда перенести операции из «${category.label}»?",
                    Keyboards.categories(targets, { "ct:${category.id}:${it.id}" }, listOf(InlineKeyboardButton("✖️ Отмена", "cn:${category.id}"))),
                )
            }
            "ct" -> {
                val category = categories.findById(ctx.first)
                val target = ctx.second?.let { categories.findById(it) }
                if (category == null || target == null || category.type != target.type) {
                    ctx.notice = "Категория уже удалена"
                    return true
                }
                categories.moveAndDelete(category.id, target.id)
                api.editMessageText(ctx.chatId, ctx.messageId, "↪️ Операции перенесены в «${target.label}», категория «${category.label}» удалена")
            }
            "ch" -> {
                val category = categories.findById(ctx.first) ?: return true.also { ctx.notice = "Категория уже удалена" }
                categories.archive(category.id)
                api.editMessageText(ctx.chatId, ctx.messageId, "🗄 Категория «${category.label}» скрыта, операции остались в отчётах")
            }
            "cn" -> api.editMessageText(ctx.chatId, ctx.messageId, "Отменено")
            else -> return false
        }
        return true
    }
}
