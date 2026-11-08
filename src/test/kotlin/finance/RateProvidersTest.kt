package finance

import finance.domain.Currency
import finance.rates.CurrencyApiProvider
import finance.rates.OpenErApiProvider
import finance.telegram.configureDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateProvidersTest {
    private val urls = mutableListOf<String>()

    private fun client(handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpClient(MockEngine { request -> urls += request.url.toString(); handler(request) }) { configureDefaults() }

    private val currencyApiBody = """
        {"date": "2026-11-07", "byn": {"usd": 0.3336, "eur": 0.2995, "rub": 27.36, "btc": 0.000003}}
    """.trimIndent()

    @Test
    fun `currency-api latest rates are inverted to BYN per unit`() = runTest {
        val snapshot = CurrencyApiProvider(client { respondJson(currencyApiBody) }).fetch(null)!!

        assertEquals(LocalDate.of(2026, 11, 7), snapshot.date)
        assertEquals(BigDecimal("2.997602"), snapshot.rates[Currency.USD])
        assertEquals(BigDecimal("3.338898"), snapshot.rates[Currency.EUR])
        assertEquals(BigDecimal("0.036550"), snapshot.rates[Currency.RUB])
        assertEquals(setOf(Currency.USD, Currency.EUR, Currency.RUB), snapshot.rates.keys)
        assertTrue(urls.single().contains("@latest"))
    }

    @Test
    fun `currency-api requests a historical date`() = runTest {
        CurrencyApiProvider(client { respondJson(currencyApiBody) }).fetch(LocalDate.of(2026, 10, 1))
        assertTrue(urls.single().contains("@2026-10-01"))
    }

    @Test
    fun `currency-api falls back to the mirror`() = runTest {
        val provider = CurrencyApiProvider(
            client { request ->
                if (request.url.host == "cdn.jsdelivr.net") respondError(HttpStatusCode.NotFound) else respondJson(currencyApiBody)
            },
        )
        assertEquals(BigDecimal("2.997602"), provider.fetch(null)!!.rates[Currency.USD])
        assertEquals(listOf("cdn.jsdelivr.net", "latest.currency-api.pages.dev"), urls.map { io.ktor.http.Url(it).host })
    }

    @Test
    fun `currency-api returns null when every source fails`() = runTest {
        val provider = CurrencyApiProvider(
            client { request ->
                if (request.url.host == "cdn.jsdelivr.net") respondError(HttpStatusCode.InternalServerError) else respondJson("not json")
            },
        )
        assertNull(provider.fetch(null))
    }

    @Test
    fun `open er-api latest rates`() = runTest {
        val body = """
            {"result": "success", "time_last_update_unix": 1794096000, "rates": {"USD": 0.3336, "EUR": 0.2995, "RUB": 27.36}}
        """.trimIndent()
        val snapshot = OpenErApiProvider(client { respondJson(body) }).fetch(null)!!

        assertEquals(LocalDate.of(2026, 11, 8), snapshot.date)
        assertEquals(BigDecimal("2.997602"), snapshot.rates[Currency.USD])
        assertEquals("https://open.er-api.com/v6/latest/BYN", urls.single())
    }

    @Test
    fun `open er-api has no history and handles errors`() = runTest {
        assertNull(OpenErApiProvider(client { respondJson("{}") }).fetch(LocalDate.of(2026, 10, 1)))
        assertTrue(urls.isEmpty())

        assertNull(OpenErApiProvider(client { respondJson("""{"result": "error"}""") }).fetch(null))
    }
}
