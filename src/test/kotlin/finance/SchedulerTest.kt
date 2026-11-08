package finance

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulerTest {
    private val app = TestApp(now = LocalDateTime.of(2026, 11, 8, 12, 0))

    private suspend fun tickAt(time: String): List<FakeTelegram.Call> {
        app.now = LocalDateTime.parse(time)
        app.telegram.clear()
        app.scheduler.tick()
        return app.telegram.calls("sendMessage")
    }

    @Test
    fun `payment is added on its day from notify time`() = runTest {
        app.send("/recurring add Netflix 10 usd 9")

        assertEquals(emptyList(), tickAt("2026-11-09T09:59"))
        val sent = tickAt("2026-11-09T10:00").single()
        assertEquals("🔁 Добавил: Netflix — −10.00 USD (≈ 30.00 BYN), 📱 Подписки и связь", sent.text)
        assertEquals(listOf("ea:1", "d:1"), sent.buttons)
        assertEquals(emptyList(), tickAt("2026-11-09T10:01"))
        assertEquals(LocalDate.of(2026, 12, 9), app.recurring.list().single().nextRun)
        assertTrue(app.send("/today").text.contains("Netflix"))
    }

    @Test
    fun `missed payments are caught up with their own dates`() = runTest {
        app.send("/recurring add Аренда 450 1")
        val sent = tickAt("2027-02-15T08:00")
        assertEquals(
            listOf(
                "🔁 Добавил: Аренда — −450.00 BYN, 🏠 Жильё · 📅 01.12.2026",
                "🔁 Добавил: Аренда — −450.00 BYN, 🏠 Жильё · 📅 01.01",
                "🔁 Добавил: Аренда — −450.00 BYN, 🏠 Жильё · 📅 01.02",
            ),
            sent.map { it.text },
        )
        assertEquals(LocalDate.of(2027, 3, 1), app.recurring.list().single().nextRun)
    }

    @Test
    fun `income payments are added as income`() = runTest {
        app.send("/recurring add +Зарплата 2000 10")
        assertEquals("🔁 Добавил: Зарплата — +2 000.00 BYN, 💰 Зарплата", tickAt("2026-11-10T10:00").single().text)
    }

    @Test
    fun `payment is postponed when the rate is unavailable`() = runTest {
        val noRates = TestApp(rates = null)
        noRates.send("/recurring add Netflix 10 usd 9")
        noRates.now = LocalDateTime.parse("2026-11-09T10:00")
        noRates.telegram.clear()
        noRates.scheduler.tick()
        assertEquals(emptyList(), noRates.telegram.calls("sendMessage"))
        assertEquals(LocalDate.of(2026, 11, 9), noRates.recurring.list().single().nextRun)
    }

    @Test
    fun `recurring payment can be cancelled or get another amount`() = runTest {
        app.send("/recurring add Netflix 10 usd 9")
        tickAt("2026-11-09T10:00")

        app.press("ea:1")
        assertEquals("Напиши новую сумму для «Netflix» в USD.\n/cancel — отмена", app.telegram.last("sendMessage").text)
        assertEquals("Это не похоже на сумму. Напиши число, например 12.50, или /cancel", app.send("дорого").text)
        val updated = app.send("12,5")
        assertEquals("✏️ Сумма изменена\n📱 Подписки и связь  −12.50 USD (≈ 37.50 BYN, курс 3.0000)\n📅 сегодня · Netflix", updated.text)
        assertTrue(app.send("кофе 5").text.startsWith("🍔 Еда"), "waiting for amount is over")

        app.press("d:1")
        assertEquals("🗑 Удалено: сегодня 📱 Подписки и связь −12.50 USD (≈ 37.50 BYN) · Netflix", app.edited().text)
        assertEquals(LocalDate.of(2026, 12, 9), app.recurring.list().single().nextRun, "schedule is kept")
    }

    @Test
    fun `recurring payment triggers budget warning`() = runTest {
        app.send("/budget жильё 500")
        app.send("/recurring add Аренда 450 9")
        assertEquals(
            "🔁 Добавил: Аренда — −450.00 BYN, 🏠 Жильё\n\n⚠️ 🏠 Жильё: 450.00 / 500.00 BYN (90%), осталось 50.00 BYN на 22 дн.",
            tickAt("2026-11-09T10:00").single().text,
        )
    }

    @Test
    fun `monthly summary is sent once after notify time`() = runTest {
        assertEquals(emptyList(), tickAt("2026-11-08T12:00"), "first run only remembers the month")
        app.send("/budget еда 600")
        app.send("кофе 60")
        app.send("+1500 зп")

        assertEquals(emptyList(), tickAt("2026-11-30T23:59"))
        assertEquals(emptyList(), tickAt("2026-12-01T09:59"))
        val summary = tickAt("2026-12-01T10:00").single()
        assertTrue(summary.text.startsWith("📊 Ноябрь 2026\nДоходы:  +1 500.00 BYN\nРасходы: −60.00 BYN"))
        assertTrue(summary.text.contains("💰 Бюджеты на Ноябрь 2026, BYN:\n🍔 Еда  60.00 / 600.00"))
        assertTrue(summary.text.endsWith("Скопировать бюджеты на Декабрь 2026?"))
        assertEquals(listOf("bc:202611", "bn:0"), summary.buttons)
        assertEquals(emptyList(), tickAt("2026-12-01T10:01"))

        app.press("bc:202611")
        assertEquals("Скопировано бюджетов: 1", app.answer())
        assertEquals(1, app.telegram.calls("editMessageReplyMarkup").size)
        assertTrue(app.send("/budget").text.startsWith("💰 Бюджеты на Декабрь 2026"))
    }

    @Test
    fun `summary without budgets has no copy offer`() = runTest {
        tickAt("2026-11-08T12:00")
        val summary = tickAt("2026-12-02T11:00").single()
        assertEquals("📊 Ноябрь 2026\nОпераций нет", summary.text)
        assertEquals(emptyList(), summary.buttons)

        app.press("bn:0")
        assertEquals(1, app.telegram.calls("editMessageReplyMarkup").size)
    }
}
