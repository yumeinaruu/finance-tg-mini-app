package finance

import finance.domain.Category
import finance.domain.CategoryType
import finance.service.CategoryMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryMatcherTest {
    private val food = Category(1, "Еда", "🍔", CategoryType.EXPENSE)
    private val transport = Category(2, "Транспорт", "🚕", CategoryType.EXPENSE)
    private val housing = Category(3, "Жильё", "🏠", CategoryType.EXPENSE)
    private val categories = listOf(food, transport, housing)
    private val aliases = mapOf("кофе" to 1L, "продукты" to 1L, "такси" to 2L, "бар" to 99L)

    private fun match(note: String?) = CategoryMatcher.match(note, categories, aliases)

    @Test
    fun `exact alias`() {
        assertEquals(food, match("кофе"))
        assertEquals(transport, match("Такси до дома"))
    }

    @Test
    fun `category name and yo normalization`() {
        assertEquals(transport, match("транспорт"))
        assertEquals(housing, match("жилье"))
    }

    @Test
    fun `normalization`() {
        assertEquals("жилье", CategoryMatcher.normalize("Жильё!"))
        assertEquals(listOf("такси", "до", "дома"), CategoryMatcher.words("  Такси, до дома. "))
        assertEquals(food, CategoryMatcher.match("ёда", listOf(food), mapOf("еда" to 1L)))
    }

    @Test
    fun `word endings`() {
        assertEquals(food, match("продуктов"))
        assertEquals(food, match("кофейня"))
    }

    @Test
    fun `unknown words and aliases of inactive categories`() {
        assertNull(match("бассейн"))
        assertNull(match("бар"))
        assertNull(match(null))
    }
}
