package finance.rates

import finance.domain.Currency
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Rates as BYN per 1 unit of currency. */
data class RateSnapshot(val date: LocalDate, val rates: Map<Currency, BigDecimal>)

interface RateProvider {
    val name: String

    /** Rates for [date], or the latest ones when [date] is null. Returns null when unavailable. */
    suspend fun fetch(date: LocalDate?): RateSnapshot?
}

private val json = Json { ignoreUnknownKeys = true }
private val log = LoggerFactory.getLogger(RateProvider::class.java)

/** Inverts "units of currency per 1 BYN" into "BYN per 1 unit of currency". */
private fun invert(perByn: (Currency) -> BigDecimal?): Map<Currency, BigDecimal> =
    Currency.entries.filter { it != Currency.BASE }.mapNotNull { currency ->
        perByn(currency)?.takeIf { it.signum() > 0 }?.let { currency to BigDecimal.ONE.divide(it, 6, RoundingMode.HALF_UP) }
    }.toMap()

private suspend fun HttpClient.loadJson(url: String): JsonObject? = try {
    val response = get(url)
    if (response.status.isSuccess()) json.parseToJsonElement(response.bodyAsText()).jsonObject else null
} catch (e: Exception) {
    log.warn("Failed to load rates from {}: {}", url, e.message)
    null
}

/**
 * https://github.com/fawazahmed0/exchange-api — free, served from CDN, has historical rates by date.
 */
class CurrencyApiProvider(private val http: HttpClient) : RateProvider {
    override val name = "currency-api"

    override suspend fun fetch(date: LocalDate?): RateSnapshot? {
        val tag = date?.toString() ?: "latest"
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/byn.json",
            "https://$tag.currency-api.pages.dev/v1/currencies/byn.json",
        )
        for (url in urls) {
            val body = http.loadJson(url) ?: continue
            val perByn = body["byn"]?.jsonObject ?: continue
            val rates = invert { perByn[it.code.lowercase()]?.jsonPrimitive?.content?.toBigDecimalOrNull() }
            val snapshotDate = body["date"]?.jsonPrimitive?.content?.let(LocalDate::parse) ?: date ?: continue
            if (rates.isNotEmpty()) return RateSnapshot(snapshotDate, rates)
        }
        return null
    }
}

/** https://open.er-api.com — free fallback, latest rates only. */
class OpenErApiProvider(private val http: HttpClient) : RateProvider {
    override val name = "open.er-api"

    override suspend fun fetch(date: LocalDate?): RateSnapshot? {
        if (date != null) return null
        val body = http.loadJson("https://open.er-api.com/v6/latest/BYN") ?: return null
        if (body["result"]?.jsonPrimitive?.content != "success") return null
        val perByn = body["rates"]?.jsonObject ?: return null
        val rates = invert { perByn[it.code]?.jsonPrimitive?.content?.toBigDecimalOrNull() }
        val updated = body["time_last_update_unix"]?.jsonPrimitive?.longOrNull
            ?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }
            ?: LocalDate.now(ZoneOffset.UTC)
        return if (rates.isEmpty()) null else RateSnapshot(updated, rates)
    }
}
