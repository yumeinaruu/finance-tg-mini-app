package finance.bot

import finance.domain.Category
import finance.domain.Operation
import finance.domain.Recurring
import finance.telegram.InlineKeyboardButton
import finance.telegram.InlineKeyboardMarkup
import java.time.YearMonth

/** Inline keyboards. Callback data format: "<action>:<id>[:<id>]". */
object Keyboards {
    private fun button(text: String, data: String) = InlineKeyboardButton(text, data)

    fun operation(op: Operation) = InlineKeyboardMarkup(
        listOf(listOf(button("🏷 Категория", "cc:${op.id}"), button("🗑 Удалить", "d:${op.id}"))),
    )

    fun recurringOperation(op: Operation) = InlineKeyboardMarkup(
        listOf(listOf(button("✏️ Другая сумма", "ea:${op.id}"), button("↩️ Отменить", "d:${op.id}"))),
    )

    /** Category buttons two per row, then a row of [extra] buttons. */
    fun categories(options: List<Category>, data: (Category) -> String, extra: List<InlineKeyboardButton>) =
        InlineKeyboardMarkup(options.map { button(it.label, data(it)) }.chunked(2) + listOf(extra))

    fun newCategoryOrCancel(pendingId: Long) = listOf(
        button("＋ Новая категория", "pn:$pendingId"),
        button("✖️ Отмена", "px:$pendingId"),
    )

    fun categoriesMenu() = InlineKeyboardMarkup(
        listOf(
            listOf(button("＋ Расход", "ca:0"), button("＋ Доход", "ca:1")),
            listOf(button("🗑 Удалить категорию", "cd:0")),
        ),
    )

    fun deleteCategory(categoryId: Long) = InlineKeyboardMarkup(
        listOf(
            listOf(button("↪️ Перенести в другую", "cm:$categoryId")),
            listOf(button("🗄 Скрыть, операции оставить", "ch:$categoryId")),
            listOf(button("✖️ Отмена", "cn:$categoryId")),
        ),
    )

    fun copyBudgets(from: YearMonth) = InlineKeyboardMarkup(
        listOf(listOf(button("✅ Скопировать", "bc:${from.year * 100 + from.monthValue}"), button("Не нужно", "bn:0"))),
    )

    fun last(ops: List<Operation>): InlineKeyboardMarkup? = numbered(ops.map { it.id }, "ld")

    fun recurring(list: List<Recurring>): InlineKeyboardMarkup? = numbered(list.map { it.id }, "rd")

    private fun numbered(ids: List<Long>, action: String): InlineKeyboardMarkup? =
        if (ids.isEmpty()) null else InlineKeyboardMarkup(ids.mapIndexed { i, id -> button("🗑 ${i + 1}", "$action:$id") }.chunked(5))
}
