package finance

import finance.db.Migrations
import finance.db.query
import finance.domain.CategoryType
import finance.repo.CategoryRepository
import finance.service.CategoryMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationsTest {
    @Test
    fun `splits statements and drops comment lines`() {
        val sql = """
            -- comment; with semicolon
            CREATE TABLE a (id INTEGER);
            INSERT INTO a VALUES (1);
        """.trimIndent()
        assertEquals(listOf("CREATE TABLE a (id INTEGER)", "INSERT INTO a VALUES (1)"), Migrations.splitStatements(sql))
    }

    @Test
    fun `applying twice is a no-op`() {
        val db = testDatabase()
        Migrations.apply(db)
        val versions = db.transaction { query("SELECT version FROM schema_version") { it.getInt(1) } }
        assertEquals(listOf(1, 2, 3), versions)
    }

    @Test
    fun `default categories and aliases are seeded`() {
        val categories = CategoryRepository(testDatabase())
        assertEquals(
            listOf("Еда", "Транспорт", "Жильё", "Подписки и связь", "Здоровье", "Покупки", "Развлечения", "Подарки", "Прочее"),
            categories.active(CategoryType.EXPENSE).map { it.name },
        )
        assertEquals(listOf("Зарплата", "Прочий доход"), categories.active(CategoryType.INCOME).map { it.name })

        val aliases = categories.aliases()
        assertTrue(aliases.size >= 50)
        aliases.keys.forEach { assertEquals(CategoryMatcher.normalize(it), it, "alias must be stored normalized") }
        assertEquals("Еда", categories.findById(aliases.getValue("кофе"))!!.name)
        assertEquals("Зарплата", categories.findById(aliases.getValue("зп"))!!.name)
    }
}
