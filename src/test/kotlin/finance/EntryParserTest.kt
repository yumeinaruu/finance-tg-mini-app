package finance

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import finance.parse.EntryParser
import finance.parse.ParseResult
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EntryParserTest {
    private val parser = EntryParser()
    private val today = LocalDate.of(2026, 11, 8)

    private fun ok(text: String): NewOperation = assertIs<ParseResult.Ok>(parser.parse(text, today), text).entry

    private fun error(text: String) = assertIs<ParseResult.Error>(parser.parse(text, today), text)

    @Test
    fun `simple expense in base currency`() {
        assertEquals(NewOperation(CategoryType.EXPENSE, 500, Currency.BYN, today, "кофе"), ok("кофе 5"))
    }

    @Test
    fun `currency as separate word`() {
        val entry = ok("такси 20 usd")
        assertEquals(2000, entry.amountMinor)
        assertEquals(Currency.USD, entry.currency)
        assertEquals("такси", entry.note)
    }

    @Test
    fun `currency symbol as prefix and suffix`() {
        assertEquals(Currency.USD, ok("\$20 такси").currency)
        assertEquals(Currency.EUR, ok("отель 100€").currency)
        assertEquals(Currency.BYN, ok("кофе 5р").currency)
        assertEquals(Currency.RUB, ok("подписка 299 ₽").currency)
    }

    @Test
    fun `comma decimal and relative date`() {
        val entry = ok("продукты 45,5 вчера")
        assertEquals(4550, entry.amountMinor)
        assertEquals(today.minusDays(1), entry.date)
    }

    @Test
    fun `dot decimal is an amount when it is the only number`() {
        val entry = ok("обед 12.50")
        assertEquals(1250, entry.amountMinor)
        assertEquals(today, entry.date)
    }

    @Test
    fun `short date next to amount`() {
        val entry = ok("аптека 12 05.11")
        assertEquals(1200, entry.amountMinor)
        assertEquals(LocalDate.of(2026, 11, 5), entry.date)
    }

    @Test
    fun `short date in the future means last year`() {
        val entry = ok("10.12 кофе 5")
        assertEquals(500, entry.amountMinor)
        assertEquals(LocalDate.of(2025, 12, 10), entry.date)
    }

    @Test
    fun `full date`() {
        assertEquals(LocalDate.of(2026, 11, 1), ok("такси 20 eur 01.11.2026").date)
    }

    @Test
    fun `income with plus`() {
        val salary = ok("+1500 зп")
        assertEquals(CategoryType.INCOME, salary.type)
        assertEquals(150000, salary.amountMinor)

        val freelance = ok("+300$ фриланс")
        assertEquals(CategoryType.INCOME, freelance.type)
        assertEquals(Currency.USD, freelance.currency)

        assertEquals(CategoryType.INCOME, ok("+ 200 кэшбэк").type)
    }

    @Test
    fun `more date formats and explicit minus`() {
        assertEquals(today.minusDays(2), ok("кофе 5 позавчера").date)
        assertEquals(LocalDate.of(2026, 11, 1), ok("кофе 5 01.11.26").date)
        val entry = ok("-5 кофе")
        assertEquals(CategoryType.EXPENSE, entry.type)
        assertEquals(500, entry.amountMinor)
    }

    @Test
    fun `amount without note`() {
        assertEquals(null, ok("15").note)
    }

    @Test
    fun `invalid input`() {
        error("кофе")
        error("кофе 0")
        error("5кофе")
        error("кофе 5 7")
        error("кофе 1.234")
        error("кофе 1234567890")
        error("кофе 5 вчера 05.11")
        error("кофе 31.02.2026 5 6")
    }
}
