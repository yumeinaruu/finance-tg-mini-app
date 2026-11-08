package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.query
import finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDate

class RateRepository(private val db: Database) {

    fun find(date: LocalDate, currency: Currency): BigDecimal? = db.transaction {
        query("SELECT rate FROM fx_rate WHERE date = ? AND currency = ?", date.toString(), currency.code) {
            BigDecimal(it.getString(1))
        }.firstOrNull()
    }

    /** Closest stored rate on or before [date], otherwise the closest after it. */
    fun findNearest(date: LocalDate, currency: Currency): BigDecimal? = db.transaction {
        val before = query(
            "SELECT rate FROM fx_rate WHERE currency = ? AND date <= ? ORDER BY date DESC LIMIT 1",
            currency.code, date.toString(),
        ) { BigDecimal(it.getString(1)) }.firstOrNull()
        before ?: query(
            "SELECT rate FROM fx_rate WHERE currency = ? AND date > ? ORDER BY date LIMIT 1",
            currency.code, date.toString(),
        ) { BigDecimal(it.getString(1)) }.firstOrNull()
    }

    fun save(date: LocalDate, rates: Map<Currency, BigDecimal>, source: String) = db.transaction {
        rates.forEach { (currency, rate) ->
            execute(
                "INSERT OR REPLACE INTO fx_rate (date, currency, rate, source) VALUES (?, ?, ?, ?)",
                date.toString(), currency.code, rate.toPlainString(), source,
            )
        }
    }
}
