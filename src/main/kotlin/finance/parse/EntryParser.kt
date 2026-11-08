package finance.parse

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.Money
import finance.domain.NewOperation
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate

sealed interface ParseResult {
    data class Ok(val entry: NewOperation) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * Parses free-form input like "кофе 5", "такси 20 usd", "+1500 зп", "аптека 12 05.11".
 * Word order is free; the decimal separator may be a dot or a comma.
 */
class EntryParser {

    fun parse(text: String, today: LocalDate): ParseResult {
        var income = false
        var currency: Currency? = null
        var date: LocalDate? = null
        val numbers = mutableListOf<NumberToken>()
        val words = mutableListOf<String>()

        for (raw in text.trim().split(WHITESPACE).filter { it.isNotEmpty() }) {
            val lower = raw.lowercase()
            val relativeDays = RELATIVE_DATES[lower]
            val fullDate = parseFullDate(lower)
            val number = parseNumber(lower, today)
            val standaloneCurrency = Currency.fromAlias(lower)
            when {
                raw == "+" -> income = true
                relativeDays != null -> date = today.minusDays(relativeDays)
                fullDate != null -> date = fullDate
                number != null -> numbers += number
                standaloneCurrency != null -> currency = currency ?: standaloneCurrency
                else -> words += raw
            }
        }

        val amount = when (numbers.size) {
            0 -> return ParseResult.Error(FORMAT_HINT)
            1 -> numbers.single()
            2 -> {
                val dateIndex = numbers.indexOfLast { it.asDate != null }
                if (dateIndex < 0 || date != null) return ParseResult.Error(AMBIGUOUS)
                date = numbers[dateIndex].asDate
                numbers[1 - dateIndex]
            }
            else -> return ParseResult.Error(AMBIGUOUS)
        }

        if (amount.value.signum() <= 0) return ParseResult.Error("Сумма должна быть больше нуля")

        return ParseResult.Ok(
            NewOperation(
                type = if (income || amount.income) CategoryType.INCOME else CategoryType.EXPENSE,
                amountMinor = Money.toMinor(amount.value),
                currency = amount.currency ?: currency ?: Currency.BASE,
                date = date ?: today,
                note = words.joinToString(" ").ifBlank { null },
            ),
        )
    }

    private data class NumberToken(
        val value: BigDecimal,
        val currency: Currency?,
        val income: Boolean,
        /** Set when the token also reads as a "dd.MM" date. */
        val asDate: LocalDate?,
    )

    private fun parseNumber(token: String, today: LocalDate): NumberToken? {
        val match = AMOUNT.matchEntire(token) ?: return null
        val (sign, prefix, digits, suffix) = match.destructured
        val currencyToken = prefix.ifEmpty { suffix }
        val currency = if (currencyToken.isEmpty()) null else Currency.fromAlias(currencyToken) ?: return null
        val asDate = if (sign.isEmpty() && currency == null) parseShortDate(digits, today) else null
        return NumberToken(BigDecimal(digits.replace(',', '.')), currency, sign == "+", asDate)
    }

    private fun parseShortDate(token: String, today: LocalDate): LocalDate? {
        val match = SHORT_DATE.matchEntire(token) ?: return null
        val (day, month) = match.destructured
        val date = safeDate(today.year, month.toInt(), day.toInt()) ?: return null
        return if (date.isAfter(today)) date.minusYears(1) else date
    }

    private fun parseFullDate(token: String): LocalDate? {
        val match = FULL_DATE.matchEntire(token) ?: return null
        val (day, month, year) = match.destructured
        val fullYear = if (year.length == 2) 2000 + year.toInt() else year.toInt()
        return safeDate(fullYear, month.toInt(), day.toInt())
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    companion object {
        const val FORMAT_HINT = "Не нашёл сумму. Пиши так: «кофе 5», «такси 20 usd», «+1500 зп», «аптека 12 вчера»"
        private const val AMBIGUOUS = "Нашёл несколько чисел и не понял, какое из них сумма. Пример: «аптека 12 05.11»"

        private val WHITESPACE = Regex("\\s+")
        private val AMOUNT = Regex("^([+-]?)([$€₽]?)(\\d{1,9}(?:[.,]\\d{1,2})?)([$€₽]|[a-zа-яё]+)?$")
        private val SHORT_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})$")
        private val FULL_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})$")
        private val RELATIVE_DATES = mapOf("сегодня" to 0L, "вчера" to 1L, "позавчера" to 2L)
    }
}
