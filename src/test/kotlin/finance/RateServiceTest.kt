package finance

import finance.domain.Currency
import finance.rates.RateService
import finance.rates.RateUnavailableException
import finance.repo.RateRepository
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RateServiceTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val repository = RateRepository(testDatabase())

    @Test
    fun `base currency rate is one`() = runTest {
        val service = RateService(repository, emptyList()) { today }
        assertEquals(BigDecimal.ONE, service.rate(Currency.BYN, today))
    }

    @Test
    fun `fetched rate is cached`() = runTest {
        val provider = FakeRateProvider()
        val service = RateService(repository, listOf(provider)) { today }

        assertEquals(BigDecimal("3.000000"), service.rate(Currency.USD, today))
        assertEquals(BigDecimal("3.000000"), service.rate(Currency.USD, today))
        assertEquals(listOf<LocalDate?>(null), provider.requests)
    }

    @Test
    fun `historical date is requested by date and falls back to next provider`() = runTest {
        val broken = FakeRateProvider(name = "broken", rates = null)
        val working = FakeRateProvider(name = "working")
        val service = RateService(repository, listOf(broken, working)) { today }
        val date = LocalDate.of(2026, 10, 1)

        assertEquals(BigDecimal("3.000000"), service.rate(Currency.USD, date))
        assertEquals(date, working.requests.single())
        assertEquals(4, broken.requests.size) // the date plus three days back
    }

    @Test
    fun `nearest stored rate is used when providers fail`() = runTest {
        repository.save(LocalDate.of(2026, 11, 1), mapOf(Currency.USD to BigDecimal("2.950000")), "test")
        val service = RateService(repository, listOf(FakeRateProvider(rates = null))) { today }

        assertEquals(BigDecimal("2.950000"), service.rate(Currency.USD, today))
    }

    @Test
    fun `fails when no rate is known`() = runTest {
        val service = RateService(repository, listOf(FakeRateProvider(rates = null))) { today }
        assertFailsWith<RateUnavailableException> { service.rate(Currency.EUR, today) }
    }
}
