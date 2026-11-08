package finance

import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import finance.domain.Schedule
import finance.repo.StateRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewRepositoriesTest {
    private val app = TestApp()
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    @Test
    fun `budget copy keeps existing limits and skips archived categories`() {
        val food = app.categoryId("Еда")
        val fun_ = app.categoryId("Развлечения")
        val gifts = app.categoryId("Подарки")
        app.budgetRepository.set(food, nov, 60000)
        app.budgetRepository.set(fun_, nov, 10000)
        app.budgetRepository.set(gifts, nov, 5000)
        app.budgetRepository.set(gifts, dec, 7000)
        app.categoryRepository.setArchived(fun_, true)

        assertEquals(1, app.budgetRepository.copy(nov, dec))
        assertEquals(60000, app.budgetRepository.find(food, dec))
        assertEquals(7000, app.budgetRepository.find(gifts, dec))
        assertNull(app.budgetRepository.find(fun_, dec))
        assertEquals(listOf("Еда", "Подарки"), app.budgetRepository.forMonth(dec).map { it.first.name })
    }

    @Test
    fun `budget alerts are remembered per month`() {
        val food = app.categoryId("Еда")
        assertFalse(app.budgetRepository.alerted(food, nov, 80))
        app.budgetRepository.markAlerted(food, nov, 80)
        app.budgetRepository.markAlerted(food, nov, 80)
        assertTrue(app.budgetRepository.alerted(food, nov, 80))
        assertFalse(app.budgetRepository.alerted(food, dec, 80))
    }

    @Test
    fun `deleting a recurring payment keeps its operations`() {
        val housing = app.categoryRepository.findById(app.categoryId("Жильё"))!!
        val id = app.recurringRepository.insert("Аренда", housing.id, 45000, Currency.BYN, Schedule.Monthly(1), LocalDate.of(2026, 12, 1))
        val opId = app.operations.insert(
            NewOperation(CategoryType.EXPENSE, 45000, Currency.BYN, LocalDate.of(2026, 11, 1), "Аренда"),
            housing.id, BigDecimal.ONE, 45000, recurringId = id,
        )
        assertEquals(1, app.recurringRepository.countByCategory(housing.id))
        assertEquals(listOf(id), app.recurringRepository.dueOn(LocalDate.of(2026, 12, 1)).map { it.id })
        assertEquals(emptyList(), app.recurringRepository.dueOn(LocalDate.of(2026, 11, 30)))

        assertTrue(app.recurringRepository.delete(id))
        assertNull(app.recurringRepository.find(id))
        assertEquals("Аренда", app.operations.find(opId)!!.note)
    }

    @Test
    fun `new categories go to the end of their type`() {
        val id = app.categoryRepository.insert("Спорт", "🏋️", CategoryType.EXPENSE)
        assertEquals(id, app.categoryRepository.active(CategoryType.EXPENSE).last().id)
        assertEquals("Прочий доход", app.categoryRepository.active(CategoryType.INCOME).last().name)
    }

    @Test
    fun `state store`() {
        val state = StateRepository(app.db)
        assertNull(state.get("k"))
        state.set("k", "1")
        state.set("k", "2")
        assertEquals("2", state.get("k"))
    }
}
