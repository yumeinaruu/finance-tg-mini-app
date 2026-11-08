package finance

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import finance.rates.RateService
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository
import finance.repo.RateRepository
import finance.service.EntryService
import finance.service.RecordResult
import finance.service.ReportService
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EntryServiceTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val db = testDatabase()
    private val operations = OperationRepository(db)
    private val service = EntryService(
        CategoryRepository(db),
        operations,
        PendingRepository(db),
        RateService(RateRepository(db), listOf(FakeRateProvider())) { today },
    )

    private fun expense(note: String?, amountMinor: Long = 500, currency: Currency = Currency.BYN) =
        NewOperation(CategoryType.EXPENSE, amountMinor, currency, today, note)

    @Test
    fun `known word is saved to its category`() = runTest {
        val saved = assertIs<RecordResult.Saved>(service.record(expense("кофе"))).operation
        assertEquals("Еда", saved.category.name)
        assertEquals(500, saved.amountBaseMinor)
    }

    @Test
    fun `foreign currency is converted to base`() = runTest {
        val saved = assertIs<RecordResult.Saved>(service.record(expense("такси", 2000, Currency.USD))).operation
        assertEquals("Транспорт", saved.category.name)
        assertEquals(6000, saved.amountBaseMinor)
    }

    @Test
    fun `unknown word asks for category and is remembered`() = runTest {
        val pending = assertIs<RecordResult.NeedCategory>(service.record(expense("бассейн")))
        val health = pending.options.first { it.name == "Здоровье" }

        val saved = service.completePending(pending.pendingId, health.id)!!
        assertEquals(health, saved.category)
        assertNull(service.completePending(pending.pendingId, health.id))

        val next = assertIs<RecordResult.Saved>(service.record(expense("бассейн утром"))).operation
        assertEquals(health, next.category)
    }

    @Test
    fun `recategorize, undo and report`() = runTest {
        val coffee = assertIs<RecordResult.Saved>(service.record(expense("кофе", 500))).operation
        service.record(expense("такси", 1000))
        service.record(NewOperation(CategoryType.INCOME, 100000, Currency.BYN, today, "зп"))

        val gifts = service.categoriesFor(CategoryType.EXPENSE).first { it.name == "Подарки" }
        assertEquals(gifts, service.recategorize(coffee.id, gifts.id)!!.category)

        assertEquals("зп", service.undo()!!.note)

        val summary = ReportService(operations).summary(today.withDayOfMonth(1), today)
        assertEquals(0, summary.incomeMinor)
        assertEquals(1500, summary.expenseMinor)
        assertEquals(listOf("Транспорт", "Подарки"), summary.expenseByCategory.map { it.category.name })
    }
}
