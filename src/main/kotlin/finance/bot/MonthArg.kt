package finance.bot

import java.time.LocalDate
import java.time.YearMonth

/** Parses the /month argument: "", "ноябрь", "нояб 2025", "11", "11.2026", "2026-11". */
object MonthArg {
    private val NUMERIC = Regex("^(\\d{1,2})(?:[.\\-/](\\d{4}))?$")
    private val ISO = Regex("^(\\d{4})-(\\d{1,2})$")
    private val BY_PREFIX: Map<String, Int> =
        Texts.MONTHS.mapIndexed { index, name -> name.take(3).lowercase() to index + 1 }.toMap() + ("мая" to 5)

    fun parse(arg: String, today: LocalDate): YearMonth? {
        val tokens = arg.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return YearMonth.from(today)

        ISO.matchEntire(tokens[0])?.let { m ->
            return safe(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        NUMERIC.matchEntire(tokens[0])?.let { m ->
            val year = m.groupValues[2].toIntOrNull()
            return resolve(m.groupValues[1].toInt(), year, today)
        }
        val month = BY_PREFIX[tokens[0].take(3)] ?: return null
        val year = tokens.getOrNull(1)?.let { it.toIntOrNull() ?: return null }
        return resolve(month, year, today)
    }

    /** Without an explicit year, a month later than the current one means last year. */
    private fun resolve(month: Int, year: Int?, today: LocalDate): YearMonth? {
        val ym = safe(year ?: today.year, month) ?: return null
        return if (year == null && ym.isAfter(YearMonth.from(today))) ym.minusYears(1) else ym
    }

    private fun safe(year: Int, month: Int): YearMonth? =
        if (month in 1..12) YearMonth.of(year, month) else null
}
