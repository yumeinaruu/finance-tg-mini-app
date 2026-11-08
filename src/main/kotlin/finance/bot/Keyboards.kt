package finance.bot

import finance.domain.Category
import finance.domain.Operation
import finance.telegram.InlineKeyboardButton
import finance.telegram.InlineKeyboardMarkup

/** Inline keyboards. Callback data format: "<action>:<id>[:<id>]". */
object Keyboards {
    fun operation(op: Operation) = InlineKeyboardMarkup(
        listOf(
            listOf(
                InlineKeyboardButton("🏷 Категория", "cc:${op.id}"),
                InlineKeyboardButton("🗑 Удалить", "d:${op.id}"),
            ),
        ),
    )

    fun categories(options: List<Category>, data: (Category) -> String, extra: InlineKeyboardButton) =
        InlineKeyboardMarkup(
            options.map { InlineKeyboardButton(it.label, data(it)) }.chunked(2) + listOf(listOf(extra)),
        )

    fun last(ops: List<Operation>): InlineKeyboardMarkup? =
        if (ops.isEmpty()) {
            null
        } else {
            InlineKeyboardMarkup(
                ops.mapIndexed { i, op -> InlineKeyboardButton("🗑 ${i + 1}", "ld:${op.id}") }.chunked(5),
            )
        }
}
