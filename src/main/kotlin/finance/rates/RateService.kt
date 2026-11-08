package finance.rates

import finance.domain.Currency
import finance.repo.RateRepository
import java.math.BigDecimal
import java.time.LocalDate

class RateUnavailableException(val currency: Currency, val date: LocalDate) :
    RuntimeException("No rate for $currency on $date")

/**
 * Resolves BYN rates: local cache first, then providers in order, then the nearest stored rate.
 */
class RateService(
    private val repository: RateRepository,
    private val providers: List<RateProvider>,
    private val today: () -> LocalDate,
) {
    suspend fun rate(currency: Currency, date: LocalDate): BigDecimal {
        if (currency == Currency.BASE) return BigDecimal.ONE
        repository.find(date, currency)?.let { return it }
        fetchAndStore(date)?.get(currency)?.let { return it }
        return repository.findNearest(date, currency) ?: throw RateUnavailableException(currency, date)
    }

    /** Rates of all non-base currencies for [date]; currencies without a rate are skipped. */
    suspend fun rates(date: LocalDate): Map<Currency, BigDecimal> =
        Currency.entries.filter { it != Currency.BASE }.mapNotNull { currency ->
            try {
                currency to rate(currency, date)
            } catch (_: RateUnavailableException) {
                null
            }
        }.toMap()

    /** Re-downloads the latest rates and stores them for today. */
    suspend fun refreshToday() {
        fetchAndStore(today())
    }

    private suspend fun fetchAndStore(date: LocalDate): Map<Currency, BigDecimal>? {
        val latest = !date.isBefore(today())
        for (provider in providers) {
            val snapshot = if (latest) {
                provider.fetch(null)
            } else {
                // historical data may be missing for some days; step back a few days
                (0L..HISTORY_LOOKBACK_DAYS).firstNotNullOfOrNull { provider.fetch(date.minusDays(it)) }
            }
            if (snapshot != null) {
                repository.save(date, snapshot.rates, provider.name)
                return snapshot.rates
            }
        }
        return null
    }

    private companion object {
        const val HISTORY_LOOKBACK_DAYS = 3L
    }
}
