package finance

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetFlowTest {
    private val app = TestApp()

    @Test
    fun `set, show and remove budgets`() = runTest {
        assertEquals("На Ноябрь 2026 бюджетов нет.\nЗадай лимит: /budget еда 600", app.send("/budget").text)

        app.send("кофе 150")
        assertEquals("✅ Бюджет на Ноябрь 2026: 🍔 Еда — 600.00 BYN\nУже потрачено: 150.00 (25%)", app.send("/budget еда 600").text)
        assertEquals("✅ Бюджет на Ноябрь 2026: 🚕 Транспорт — 99.50 BYN\nУже потрачено: 0.00 (0%)", app.send("/budget такси 99,5").text)

        assertEquals(
            """
            💰 Бюджеты на Ноябрь 2026, BYN:
            🍔 Еда  150.00 / 600.00  ███░░░░░░░ 25%
            🚕 Транспорт  0.00 / 99.50  ░░░░░░░░░░ 0%
            Итого: 150.00 / 699.50
            """.trimIndent(),
            app.send("/budget").text,
        )

        assertEquals("Убрал бюджет для 🚕 Транспорт", app.send("/budget транспорт 0").text)
        assertEquals("Бюджета для 🚕 Транспорт не было", app.send("/budget транспорт -").text)
    }

    @Test
    fun `invalid budget commands`() = runTest {
        assertTrue(app.send("/budget еда").text.startsWith("Бюджеты на текущий месяц"))
        assertTrue(app.send("/budget еда много").text.startsWith("Бюджеты на текущий месяц"))
        assertEquals("Не нашёл категорию расходов «зп». Список: /categories", app.send("/budget зп 100").text)
    }

    @Test
    fun `warnings at 80 and 100 percent are sent once`() = runTest {
        app.send("/budget еда 600")

        assertEquals("🍔 Еда  −400.00 BYN\n📅 сегодня · продукты", app.send("продукты 400").text)
        assertEquals(
            "🍔 Еда  −80.00 BYN\n📅 сегодня · обед\n\n⚠️ 🍔 Еда: 480.00 / 600.00 BYN (80%), осталось 120.00 BYN на 23 дн.",
            app.send("обед 80").text,
        )
        assertEquals("🍔 Еда  −10.00 BYN\n📅 сегодня · кофе", app.send("кофе 10").text)
        assertEquals(
            "🍔 Еда  −110.00 BYN\n📅 сегодня · ужин\n\n🚨 🍔 Еда: бюджет превышен — 600.00 / 600.00 BYN",
            app.send("ужин 110").text,
        )
        assertEquals("🍔 Еда  −5.00 BYN\n📅 сегодня · кофе", app.send("кофе 5").text)
        assertTrue(app.send("/budget").text.contains("🍔 Еда  605.00 / 600.00  ██████████ 100% 🚨"))
    }

    @Test
    fun `jumping straight past the limit sends only the 100 percent warning`() = runTest {
        app.send("/budget еда 100")
        assertTrue(app.send("продукты 150").text.endsWith("🚨 🍔 Еда: бюджет превышен — 150.00 / 100.00 BYN"))
        assertFalse(app.send("кофе 1").text.contains("⚠️"))
    }

    @Test
    fun `foreign currency, recategorized and past-month operations are checked`() = runTest {
        app.send("/budget транспорт 100")
        assertTrue(app.send("такси 30 usd").text.endsWith("⚠️ 🚕 Транспорт: 90.00 / 100.00 BYN (90%), осталось 10.00 BYN на 23 дн."))
        assertTrue(app.send("такси 30 usd").text.endsWith("🚨 🚕 Транспорт: бюджет превышен — 180.00 / 100.00 BYN"))

        app.send("/budget подарки 50")
        app.send("кофе 45")
        app.press("sc:3:${app.categoryId("Подарки")}")
        assertTrue(app.edited().text.endsWith("⚠️ 🎁 Подарки: 45.00 / 50.00 BYN (90%), осталось 5.00 BYN на 23 дн."))

        assertFalse(app.send("такси 500 05.10").text.contains("🚨"), "no budget in October")
    }

    @Test
    fun `income never triggers budget warnings`() = runTest {
        app.send("/budget еда 10")
        assertEquals("💰 Зарплата  +1 500.00 BYN\n📅 сегодня · зп", app.send("+1500 зп").text)
    }

    @Test
    fun `month report includes budgets and budgets can be copied`() = runTest {
        app.send("/budget еда 600")
        app.send("кофе 60")
        assertTrue(app.send("/month").text.endsWith("💰 Бюджеты на Ноябрь 2026, BYN:\n🍔 Еда  60.00 / 600.00  █░░░░░░░░░ 10%\nИтого: 60.00 / 600.00"))

        app.now = app.now.plusMonths(1)
        assertEquals("📋 Скопировал с прошлого месяца: 1", app.send("/budget copy").text)
        assertEquals("Нечего копировать", app.send("/budget copy").text)
        assertTrue(app.send("/budget").text.startsWith("💰 Бюджеты на Декабрь 2026, BYN:\n🍔 Еда  0.00 / 600.00"))
    }
}
