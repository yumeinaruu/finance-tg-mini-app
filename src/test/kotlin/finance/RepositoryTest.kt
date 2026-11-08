package finance

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository
import finance.repo.RateRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private val db = testDatabase()
    private val categories = CategoryRepository(db)
    private val operations = OperationRepository(db)
    private val food = categories.active(CategoryType.EXPENSE).first()

    private fun add(date: String, note: String) = operations.insert(
        NewOperation(CategoryType.EXPENSE, 100, Currency.BYN, LocalDate.parse(date), note),
        food.id, BigDecimal.ONE, 100,
    )

    @Test
    fun `operation round trip keeps all fields`() {
        val id = operations.insert(
            NewOperation(CategoryType.EXPENSE, 2000, Currency.USD, LocalDate.of(2026, 11, 5), null),
            food.id, BigDecimal("2.997602"), 5995,
        )
        val op = operations.find(id)!!
        assertEquals(food, op.category)
        assertEquals(2000, op.amountMinor)
        assertEquals(Currency.USD, op.currency)
        assertEquals(BigDecimal("2.997602"), op.rate)
        assertEquals(5995, op.amountBaseMinor)
        assertEquals(LocalDate.of(2026, 11, 5), op.date)
        assertNull(op.note)
    }

    @Test
    fun `between is inclusive and ordered by date`() {
        add("2026-10-31", "before")
        add("2026-11-30", "last day")
        add("2026-11-01", "first day")
        add("2026-12-01", "after")

        val notes = operations.between(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).map { it.note }
        assertEquals(listOf("first day", "last day"), notes)
    }

    @Test
    fun `last returns most recently entered first, delete and update`() {
        val first = add("2026-11-08", "a")
        val second = add("2026-11-01", "b")
        assertEquals(listOf(second, first), operations.last(10).map { it.id })

        val gifts = categories.active(CategoryType.EXPENSE).first { it.name == "Подарки" }
        assertTrue(operations.updateCategory(first, gifts.id))
        assertEquals(gifts, operations.find(first)!!.category)

        assertTrue(operations.delete(first))
        assertFalse(operations.delete(first))
        assertNull(operations.find(first))
    }

    @Test
    fun `saving alias overrides existing one`() {
        val transport = categories.active(CategoryType.EXPENSE).first { it.name == "Транспорт" }
        categories.saveAlias("кофе", transport.id)
        assertEquals(transport.id, categories.aliases()["кофе"])
    }

    @Test
    fun `pending operation round trip`() {
        val pending = PendingRepository(db)
        val entry = NewOperation(CategoryType.INCOME, 30000, Currency.USD, LocalDate.of(2026, 11, 2), "фриланс")
        val id = pending.insert(entry)
        assertEquals(entry, pending.find(id))
        assertTrue(pending.delete(id))
        assertNull(pending.find(id))
    }

    @Test
    fun `nearest rate prefers earlier date, then later`() {
        val rates = RateRepository(db)
        rates.save(LocalDate.of(2026, 11, 3), mapOf(Currency.USD to BigDecimal("2.9")), "t")
        rates.save(LocalDate.of(2026, 11, 6), mapOf(Currency.USD to BigDecimal("3.1")), "t")

        assertEquals(BigDecimal("2.9"), rates.findNearest(LocalDate.of(2026, 11, 5), Currency.USD))
        assertEquals(BigDecimal("3.1"), rates.findNearest(LocalDate.of(2026, 11, 7), Currency.USD))
        assertEquals(BigDecimal("2.9"), rates.findNearest(LocalDate.of(2026, 11, 1), Currency.USD))
        assertNull(rates.findNearest(LocalDate.of(2026, 11, 5), Currency.EUR))
        assertNull(rates.find(LocalDate.of(2026, 11, 5), Currency.USD))
    }
}
