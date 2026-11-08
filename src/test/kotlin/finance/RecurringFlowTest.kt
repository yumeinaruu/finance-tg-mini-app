package finance

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringFlowTest {
    private val app = TestApp() // Sunday, 08.11.2026

    @Test
    fun `payment with a known category is created right away`() = runTest {
        assertEquals(
            "🔁 Добавил регулярный платёж:\nNetflix — 10.00 USD · 📱 Подписки и связь · каждый месяц 15-го\nПервое списание: 15.11",
            app.send("/recurring add Netflix 10 usd 15").text,
        )
        assertEquals(
            "🔁 Добавил регулярный платёж:\nАренда — 450.00 BYN · 🏠 Жильё · каждый месяц 8-го\nПервое списание: 08.12",
            app.send("/recurring add Аренда 450 8").text,
        )
        assertEquals(
            "🔁 Добавил регулярный платёж:\nЗарплата — 2 000.00 BYN · 💰 Зарплата · каждый месяц 5-го\nПервое списание: 05.12",
            app.send("/recurring add +Зарплата 2000 5").text,
        )
        assertEquals(
            "🔁 Регулярные платежи:\n" +
                "1. Netflix — 10.00 USD · 📱 Подписки и связь · каждый месяц 15-го · след. 15.11\n" +
                "2. Зарплата — 2 000.00 BYN · 💰 Зарплата · каждый месяц 5-го · след. 05.12\n" +
                "3. Аренда — 450.00 BYN · 🏠 Жильё · каждый месяц 8-го · след. 08.12",
            app.send("/recurring").text,
        )
    }

    @Test
    fun `unknown name asks for a category`() = runTest {
        val question = app.send("/recurring add Спортзал 30 пн")
        assertEquals("Какую категорию поставить для «Спортзал»?", question.text)
        val health = app.categoryId("Здоровье")
        assertTrue("rc:1:$health" in question.buttons)
        assertEquals("rx:1", question.buttons.last())

        app.press("rc:1:$health")
        assertEquals(
            "🔁 Добавил регулярный платёж:\nСпортзал — 30.00 BYN · 💊 Здоровье · по понедельникам\nПервое списание: 09.11",
            app.edited().text,
        )
        app.press("rc:1:$health")
        assertEquals("Черновик устарел, добавь платёж заново", app.edited().text)

        app.send("/recurring add Йога 20 01.03")
        app.press("rx:2")
        assertEquals("✖️ Не добавлено", app.edited().text)
        assertEquals(1, app.recurring.list().size)
    }

    @Test
    fun `payments are deleted from the list`() = runTest {
        assertTrue(app.send("/recurring").text.startsWith("Регулярных платежей нет."))
        app.send("/recurring add Netflix 10 usd 15")
        app.send("/recurring add Аренда 450 1")
        assertEquals(listOf("rd:1", "rd:2"), app.send("/recurring").buttons)

        app.press("rd:1")
        assertEquals("Удалено", app.answer())
        assertTrue(app.edited().text.startsWith("🔁 Регулярные платежи:\n1. Аренда"))
        assertEquals(listOf("rd:2"), app.edited().buttons)
        app.press("rd:1")
        assertEquals("Уже удалено", app.answer())
    }

    @Test
    fun `invalid formats show help`() = runTest {
        listOf("/recurring add", "/recurring add Netflix 15", "/recurring add Netflix 10 завтра", "/recurring add 10 15", "/recurring foo")
            .forEach { assertTrue(app.send(it).text.startsWith("Формат: /recurring add"), it) }
    }
}
