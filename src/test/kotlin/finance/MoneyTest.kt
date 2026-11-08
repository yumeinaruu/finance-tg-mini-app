package finance

import finance.domain.Currency
import finance.domain.Money
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `converts to minor units`() {
        assertEquals(4550, Money.toMinor(BigDecimal("45.5")))
        assertEquals(100, Money.toMinor(BigDecimal("1")))
        assertFailsWith<ArithmeticException> { Money.toMinor(BigDecimal("1.234")) }
    }

    @Test
    fun `converts to base with half-up rounding`() {
        assertEquals(6000, Money.toBase(2000, BigDecimal("3.000000")))
        assertEquals(5995, Money.toBase(2000, BigDecimal("2.997602")))
        assertEquals(1, Money.toBase(15, BigDecimal("0.036627"))) // 0.549 -> 1
    }

    @Test
    fun `formats with thousands separator and without sign`() {
        assertEquals("0.05", Money.format(5))
        assertEquals("1 735.20", Money.format(173520))
        assertEquals("1 000 000.00", Money.format(100_000_000))
        assertEquals("12.00", Money.format(-1200))
    }

    @Test
    fun `currency aliases`() {
        assertEquals(Currency.USD, Currency.fromAlias("$"))
        assertEquals(Currency.USD, Currency.fromAlias("USD"))
        assertEquals(Currency.BYN, Currency.fromAlias("руб"))
        assertEquals(Currency.RUB, Currency.fromAlias("₽"))
        assertEquals(null, Currency.fromAlias("кофе"))
    }
}
