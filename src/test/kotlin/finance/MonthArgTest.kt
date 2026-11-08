package finance

import finance.bot.MonthArg
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthArgTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `parses month arguments`() {
        assertEquals(YearMonth.of(2026, 11), MonthArg.parse("", today))
        assertEquals(YearMonth.of(2026, 10), MonthArg.parse("октябрь", today))
        assertEquals(YearMonth.of(2026, 5), MonthArg.parse("мая", today))
        assertEquals(YearMonth.of(2025, 12), MonthArg.parse("декабрь", today))
        assertEquals(YearMonth.of(2024, 12), MonthArg.parse("дек 2024", today))
        assertEquals(YearMonth.of(2026, 10), MonthArg.parse("10", today))
        assertEquals(YearMonth.of(2025, 3), MonthArg.parse("03.2025", today))
        assertEquals(YearMonth.of(2025, 3), MonthArg.parse("2025-03", today))
        assertNull(MonthArg.parse("13", today))
        assertNull(MonthArg.parse("абв", today))
    }
}
