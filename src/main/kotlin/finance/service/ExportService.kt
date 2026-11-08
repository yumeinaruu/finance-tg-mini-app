package finance.service

import finance.domain.CategoryType
import finance.domain.Money
import finance.repo.OperationRepository
import java.time.LocalDate

/** CSV export for spreadsheets: semicolon-separated, UTF-8 with BOM so Excel opens Cyrillic correctly. */
class ExportService(private val operations: OperationRepository) {

    fun csv(from: LocalDate, to: LocalDate): String = buildString {
        append('﻿')
        appendLine("date;type;category;amount;currency;rate;amount_byn;note")
        operations.between(from, to).forEach { op ->
            val type = if (op.category.type == CategoryType.INCOME) "income" else "expense"
            val fields = listOf(
                op.date.toString(),
                type,
                op.category.name,
                Money.format(op.amountMinor).replace(" ", ""),
                op.currency.code,
                op.rate.toPlainString(),
                Money.format(op.amountBaseMinor).replace(" ", ""),
                op.note.orEmpty(),
            )
            appendLine(fields.joinToString(";") { escape(it) })
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
