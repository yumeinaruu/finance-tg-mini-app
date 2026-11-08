package finance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** When a recurring payment happens. */
sealed interface Schedule {
    /** The first payment date strictly after [date]. */
    fun nextAfter(date: LocalDate): LocalDate

    fun encode(): String

    fun describe(): String

    /** Day 29–31 falls on the last day of shorter months. */
    data class Monthly(val day: Int) : Schedule {
        init {
            require(day in 1..31) { "Invalid day $day" }
        }

        override fun nextAfter(date: LocalDate): LocalDate {
            val month = YearMonth.from(date)
            val candidate = at(month)
            return if (candidate.isAfter(date)) candidate else at(month.plusMonths(1))
        }

        private fun at(month: YearMonth) = month.atDay(minOf(day, month.lengthOfMonth()))

        override fun encode() = "monthly:$day"

        override fun describe() = "каждый месяц $day-го"
    }

    data class Weekly(val day: DayOfWeek) : Schedule {
        override fun nextAfter(date: LocalDate): LocalDate = date.with(TemporalAdjusters.next(day))

        override fun encode() = "weekly:${day.value}"

        override fun describe() = WEEKLY_NAMES.getValue(day)
    }

    /** 29.02 falls on 28.02 in non-leap years. */
    data class Yearly(val month: Int, val day: Int) : Schedule {
        init {
            require(month in 1..12 && day in 1..Month.of(month).maxLength()) { "Invalid date $day.$month" }
        }

        override fun nextAfter(date: LocalDate): LocalDate {
            val candidate = at(date.year)
            return if (candidate.isAfter(date)) candidate else at(date.year + 1)
        }

        private fun at(year: Int): LocalDate {
            val ym = YearMonth.of(year, month)
            return ym.atDay(minOf(day, ym.lengthOfMonth()))
        }

        override fun encode() = "yearly:$month-$day"

        override fun describe() = "каждый год %02d.%02d".format(day, month)
    }

    companion object {
        private val WEEKLY_NAMES = mapOf(
            DayOfWeek.MONDAY to "по понедельникам",
            DayOfWeek.TUESDAY to "по вторникам",
            DayOfWeek.WEDNESDAY to "по средам",
            DayOfWeek.THURSDAY to "по четвергам",
            DayOfWeek.FRIDAY to "по пятницам",
            DayOfWeek.SATURDAY to "по субботам",
            DayOfWeek.SUNDAY to "по воскресеньям",
        )

        private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
            "пн" to DayOfWeek.MONDAY, "понедельник" to DayOfWeek.MONDAY,
            "вт" to DayOfWeek.TUESDAY, "вторник" to DayOfWeek.TUESDAY,
            "ср" to DayOfWeek.WEDNESDAY, "среда" to DayOfWeek.WEDNESDAY, "среду" to DayOfWeek.WEDNESDAY,
            "чт" to DayOfWeek.THURSDAY, "четверг" to DayOfWeek.THURSDAY,
            "пт" to DayOfWeek.FRIDAY, "пятница" to DayOfWeek.FRIDAY, "пятницу" to DayOfWeek.FRIDAY,
            "сб" to DayOfWeek.SATURDAY, "суббота" to DayOfWeek.SATURDAY, "субботу" to DayOfWeek.SATURDAY,
            "вс" to DayOfWeek.SUNDAY, "воскресенье" to DayOfWeek.SUNDAY,
        )

        private val DAY_OF_MONTH = Regex("^(\\d{1,2})(?:-?го)?$")
        private val DAY_MONTH = Regex("^(\\d{1,2})\\.(\\d{1,2})$")

        /** "15" / "15-го" — monthly, "пн" / "пятница" — weekly, "01.03" — yearly. */
        fun parse(token: String): Schedule? {
            val value = token.lowercase()
            WEEKDAYS[value]?.let { return Weekly(it) }
            DAY_OF_MONTH.matchEntire(value)?.let { m ->
                val day = m.groupValues[1].toInt()
                return if (day in 1..31) Monthly(day) else null
            }
            DAY_MONTH.matchEntire(value)?.let { m ->
                return runCatching { Yearly(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
            }
            return null
        }

        fun decode(value: String): Schedule {
            val (kind, arg) = value.split(':', limit = 2)
            return when (kind) {
                "monthly" -> Monthly(arg.toInt())
                "weekly" -> Weekly(DayOfWeek.of(arg.toInt()))
                "yearly" -> arg.split('-').let { (month, day) -> Yearly(month.toInt(), day.toInt()) }
                else -> error("Unknown schedule $value")
            }
        }
    }
}
