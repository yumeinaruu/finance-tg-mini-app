package finance

import finance.bot.Texts
import finance.domain.CategoryType
import finance.service.CategoryInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryInputTest {
    @Test
    fun `parses name, emoji and type`() {
        assertEquals(CategoryInput("Спорт", "🏋️", CategoryType.EXPENSE), CategoryInput.parse("спорт 🏋️"))
        assertEquals(CategoryInput("Спорт", "🏋️", CategoryType.EXPENSE), CategoryInput.parse("🏋️ Спорт"))
        assertEquals(CategoryInput("Кэшбэк", "💸", CategoryType.INCOME), CategoryInput.parse("+Кэшбэк 💸"))
        assertEquals(CategoryInput("Кэшбэк", null, CategoryType.INCOME), CategoryInput.parse("+ Кэшбэк"))
        assertEquals(CategoryInput("Дом и сад", null, CategoryType.EXPENSE), CategoryInput.parse("дом и сад"))
        assertEquals(CategoryInput("", "📺", CategoryType.EXPENSE), CategoryInput.parse("📺"))
        assertNull(CategoryInput.parse("   "))
    }

    @Test
    fun `russian plurals`() {
        val forms = listOf(1, 2, 5, 11, 12, 21, 22, 25, 111).map { Texts.plural(it, "операция", "операции", "операций") }
        assertEquals(
            listOf("операция", "операции", "операций", "операций", "операций", "операция", "операции", "операций", "операций"),
            forms,
        )
    }
}
