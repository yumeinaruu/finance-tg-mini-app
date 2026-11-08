package finance

import finance.bot.Texts
import finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class TextsTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `date labels`() {
        assertEquals("сегодня", Texts.date(today, today))
        assertEquals("вчера", Texts.date(today.minusDays(1), today))
        assertEquals("05.11", Texts.date(LocalDate.of(2026, 11, 5), today))
        assertEquals("10.12.2025", Texts.date(LocalDate.of(2025, 12, 10), today))
    }

    @Test
    fun `titles`() {
        assertEquals("📊 Ноябрь 2026", Texts.monthTitle(YearMonth.of(2026, 11)))
        assertEquals("📊 Неделя 02.11–08.11", Texts.weekTitle(LocalDate.of(2026, 11, 2), today))
    }

    @Test
    fun `rates listing shows RUB per 100`() {
        val text = Texts.rates(today, linkedMapOf(Currency.USD to BigDecimal("3.000995"), Currency.RUB to BigDecimal("0.036627")))
        assertEquals("💱 Курсы на 08.11 (к BYN):\nUSD  3.0010\n100 RUB  3.6627", text)
        assertEquals("Не удалось получить курсы, попробуй позже", Texts.rates(today, emptyMap()))
    }
}
