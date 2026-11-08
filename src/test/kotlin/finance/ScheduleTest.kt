package finance

import finance.domain.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTest {
    private fun d(value: String) = LocalDate.parse(value)

    @Test
    fun `parses tokens`() {
        assertEquals(Schedule.Monthly(15), Schedule.parse("15"))
        assertEquals(Schedule.Monthly(1), Schedule.parse("1-го"))
        assertEquals(Schedule.Monthly(31), Schedule.parse("31го"))
        assertEquals(Schedule.Weekly(DayOfWeek.MONDAY), Schedule.parse("пн"))
        assertEquals(Schedule.Weekly(DayOfWeek.FRIDAY), Schedule.parse("Пятницу"))
        assertEquals(Schedule.Yearly(3, 1), Schedule.parse("01.03"))
        assertEquals(Schedule.Yearly(2, 29), Schedule.parse("29.02"))
        listOf("0", "32", "31.02", "13.13", "завтра", "15.5.5").forEach { assertNull(Schedule.parse(it), it) }
    }

    @Test
    fun `monthly is strictly after and clamps to month end`() {
        val monthly = Schedule.Monthly(15)
        assertEquals(d("2026-11-15"), monthly.nextAfter(d("2026-11-08")))
        assertEquals(d("2026-12-15"), monthly.nextAfter(d("2026-11-15")))

        val lastDay = Schedule.Monthly(31)
        assertEquals(d("2027-02-28"), lastDay.nextAfter(d("2027-01-31")))
        assertEquals(d("2027-03-31"), lastDay.nextAfter(d("2027-02-28")))
        assertEquals(d("2028-02-29"), lastDay.nextAfter(d("2028-01-31")))
    }

    @Test
    fun `weekly and yearly`() {
        val monday = Schedule.Weekly(DayOfWeek.MONDAY)
        assertEquals(d("2026-11-09"), monday.nextAfter(d("2026-11-08")))
        assertEquals(d("2026-11-16"), monday.nextAfter(d("2026-11-09")))

        assertEquals(d("2027-03-01"), Schedule.Yearly(3, 1).nextAfter(d("2026-11-08")))
        assertEquals(d("2027-02-28"), Schedule.Yearly(2, 29).nextAfter(d("2026-11-08")))
        assertEquals(d("2028-02-29"), Schedule.Yearly(2, 29).nextAfter(d("2027-11-08")))
    }

    @Test
    fun `encode, decode and describe`() {
        listOf(Schedule.Monthly(15), Schedule.Weekly(DayOfWeek.SUNDAY), Schedule.Yearly(12, 31)).forEach {
            assertEquals(it, Schedule.decode(it.encode()))
        }
        assertEquals("каждый месяц 15-го", Schedule.Monthly(15).describe())
        assertEquals("по средам", Schedule.Weekly(DayOfWeek.WEDNESDAY).describe())
        assertEquals("каждый год 01.03", Schedule.Yearly(3, 1).describe())
    }
}
