package finance

import finance.bot.BotHandler
import finance.domain.CategoryType
import finance.domain.Currency
import finance.parse.EntryParser
import finance.rates.RateService
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository
import finance.repo.RateRepository
import finance.service.EntryService
import finance.service.ReportService
import finance.telegram.CallbackQuery
import finance.telegram.Chat
import finance.telegram.Message
import finance.telegram.User
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** End-to-end scenarios: real parser, services and SQLite; fake Telegram and rates. */
class BotHandlerTest {
    private val chatId = 42L
    private val today = LocalDate.of(2026, 11, 8)
    private val telegram = FakeTelegram()
    private val db = testDatabase()
    private val categories = CategoryRepository(db)

    private fun handler(
        rates: Map<Currency, BigDecimal>? = mapOf(
            Currency.USD to BigDecimal("3.000000"),
            Currency.EUR to BigDecimal("3.400000"),
            Currency.RUB to BigDecimal("0.036600"),
        ),
    ): BotHandler {
        val rateService = RateService(RateRepository(db), listOf(FakeRateProvider(rates = rates))) { today }
        val operations = OperationRepository(db)
        val entries = EntryService(categories, operations, PendingRepository(db), rateService)
        return BotHandler(telegram.api, EntryParser(), entries, ReportService(operations), rateService) { today }
    }

    private val bot = handler()

    private suspend fun send(text: String, handler: BotHandler = bot): FakeTelegram.Call {
        telegram.clear()
        handler.onMessage(Message(100, User(chatId), Chat(chatId), text))
        return telegram.last("sendMessage")
    }

    private suspend fun press(data: String) {
        telegram.clear()
        bot.onCallback(CallbackQuery("cb", User(chatId), Message(200, chat = Chat(chatId)), data))
    }

    private fun answer() = telegram.last("answerCallbackQuery").body["text"]?.jsonPrimitive?.content

    private fun categoryId(name: String) =
        CategoryType.entries.flatMap { categories.active(it) }.first { it.name == name }.id

    @Test
    fun `known expense is saved with buttons`() = runTest {
        val reply = send("кофе 5")
        assertEquals("🍔 Еда  −5.00 BYN\n📅 сегодня · кофе", reply.text)
        assertEquals(listOf("cc:1", "d:1"), reply.buttons)
    }

    @Test
    fun `foreign currency shows conversion and rate`() = runTest {
        assertEquals("🚕 Транспорт  −20.00 USD (≈ 60.00 BYN, курс 3.0000)\n📅 05.11 · такси", send("такси 20 usd 05.11").text)
        assertEquals(
            "🛍 Покупки  −1 000.00 RUB (≈ 36.60 BYN, курс 3.6600 за 100)\n📅 сегодня · одежда",
            send("одежда 1000 ₽").text,
        )
    }

    @Test
    fun `income is saved to income category`() = runTest {
        assertEquals("💰 Зарплата  +1 500.00 BYN\n📅 сегодня · зп", send("+1500 зп").text)
    }

    @Test
    fun `invalid input gets a hint`() = runTest {
        assertEquals(EntryParser.FORMAT_HINT, send("кофе").text)
    }

    @Test
    fun `missing rate is reported`() = runTest {
        val reply = send("такси 20 usd", handler(rates = null))
        assertEquals("Не удалось получить курс USD на 08.11.2026, попробуй позже", reply.text)
    }

    @Test
    fun `unknown word asks for category, then remembers it`() = runTest {
        val question = send("бассейн 15")
        assertEquals("Не знаю категорию для «бассейн» — −15.00 BYN. Куда записать?", question.text)
        val health = categoryId("Здоровье")
        assertTrue("pc:1:$health" in question.buttons)
        assertEquals("px:1", question.buttons.last())
        assertFalse("pc:1:${categoryId("Зарплата")}" in question.buttons, "only expense categories are offered")

        press("pc:1:$health")
        val saved = telegram.last("editMessageText")
        assertEquals("💊 Здоровье  −15.00 BYN\n📅 сегодня · бассейн\n🧠 Запомнил: «бассейн» → 💊 Здоровье", saved.text)
        assertEquals(listOf("cc:1", "d:1"), saved.buttons)

        assertTrue(send("бассейн 10").text.startsWith("💊 Здоровье"))
    }

    @Test
    fun `income without note offers income categories`() = runTest {
        val question = send("+100")
        assertEquals("Куда записать +100.00 BYN?", question.text)
        assertTrue("pc:1:${categoryId("Зарплата")}" in question.buttons)
        assertFalse("pc:1:${categoryId("Еда")}" in question.buttons)
    }

    @Test
    fun `cancelled entry cannot be completed later`() = runTest {
        send("бассейн 15")
        press("px:1")
        assertEquals("✖️ Не записано", telegram.last("editMessageText").text)

        press("pc:1:${categoryId("Здоровье")}")
        assertEquals("Эта запись уже обработана", telegram.last("editMessageText").text)
        assertTrue(send("/today").text.startsWith("Сегодня операций нет"))
    }

    @Test
    fun `category is changed with buttons`() = runTest {
        send("кофе 5")
        press("cc:1")
        val keyboard = telegram.last("editMessageReplyMarkup").buttons
        assertTrue("sc:1:${categoryId("Подарки")}" in keyboard)
        assertEquals("cb:1", keyboard.last())

        press("cb:1")
        assertEquals(listOf("cc:1", "d:1"), telegram.last("editMessageReplyMarkup").buttons)

        press("sc:1:${categoryId("Подарки")}")
        assertEquals("🎁 Подарки  −5.00 BYN\n📅 сегодня · кофе", telegram.last("editMessageText").text)
        assertEquals("Категория: 🎁 Подарки", answer())

        assertTrue(send("кофе 3").text.startsWith("🎁 Подарки"), "new category is remembered")
    }

    @Test
    fun `operation is deleted with button`() = runTest {
        send("кофе 5")
        press("d:1")
        assertEquals("🗑 Удалено: сегодня 🍔 Еда −5.00 BYN · кофе", telegram.last("editMessageText").text)

        press("d:1")
        assertEquals("Операция уже удалена", answer())
        press("cc:1")
        assertEquals("Операция уже удалена", answer())
    }

    @Test
    fun `last list deletes and refreshes`() = runTest {
        send("кофе 5")
        send("такси 10")
        val list = send("/last")
        assertEquals("Последние операции:\n1. сегодня 🚕 Транспорт −10.00 BYN · такси\n2. сегодня 🍔 Еда −5.00 BYN · кофе", list.text)
        assertEquals(listOf("ld:2", "ld:1"), list.buttons)

        press("ld:2")
        val refreshed = telegram.last("editMessageText")
        assertEquals("Последние операции:\n1. сегодня 🍔 Еда −5.00 BYN · кофе", refreshed.text)
        assertEquals(listOf("ld:1"), refreshed.buttons)
        assertEquals("Удалено", answer())

        press("ld:2")
        assertEquals("Уже удалено", answer())
    }

    @Test
    fun `undo removes the last entered operation`() = runTest {
        assertEquals("Нечего отменять", send("/undo").text)
        send("кофе 5")
        send("такси 10 вчера")
        assertEquals("↩️ Удалил: вчера 🚕 Транспорт −10.00 BYN · такси", send("/undo").text)
        assertEquals("↩️ Удалил: сегодня 🍔 Еда −5.00 BYN · кофе", send("/undo").text)
    }

    @Test
    fun `today report`() = runTest {
        send("кофе 5")
        send("такси 10 usd")
        send("+1500 зп")
        send("обед 20 вчера")
        assertEquals(
            """
            📅 Сегодня, 08.11
            🍔 Еда −5.00 BYN · кофе
            🚕 Транспорт −10.00 USD (≈ 30.00 BYN) · такси
            💰 Зарплата +1 500.00 BYN · зп

            Расходы: 35.00 BYN
            Доходы: 1 500.00 BYN
            """.trimIndent(),
            send("/today@finance_bot").text,
        )
    }

    @Test
    fun `month and week reports`() = runTest {
        send("продукты 75")
        send("такси 25")
        send("+1500 зп")
        send("кино 10 01.10")

        assertEquals(
            """
            📊 Ноябрь 2026
            Доходы:  +1 500.00 BYN
            Расходы: −100.00 BYN
            Баланс:  +1 400.00 BYN

            Расходы по категориям:
            🍔 Еда  75.00  ████████░░ 75%
            🚕 Транспорт  25.00  ███░░░░░░░ 25%
            """.trimIndent(),
            send("/month").text,
        )
        assertTrue(send("/month октябрь").text.startsWith("📊 Октябрь 2026\nДоходы:  +0.00 BYN\nРасходы: −10.00 BYN\nБаланс:  −10.00 BYN"))
        assertEquals("📊 Сентябрь 2026\nОпераций нет", send("/month 09.2026").text)
        assertEquals("Не понял месяц. Примеры: /month, /month октябрь, /month 10.2026", send("/month 13").text)
        assertTrue(send("/week").text.startsWith("📊 Неделя 02.11–08.11\nДоходы:  +1 500.00 BYN\nРасходы: −100.00 BYN"))
    }

    @Test
    fun `rate command`() = runTest {
        assertEquals("💱 Курсы на 08.11 (к BYN):\nUSD  3.0000\nEUR  3.4000\n100 RUB  3.6600", send("/rate").text)
        assertEquals("💱 Курсы на 08.11 (к BYN):\nEUR  3.4000", send("/rate €").text)
        assertEquals("Не знаю валюту «xyz»", send("/rate xyz").text)
    }

    @Test
    fun `help and unknown commands`() = runTest {
        assertTrue(send("/start").text.startsWith("Привет!"))
        assertEquals("Не знаю такую команду. /help — список команд", send("/foo").text)
    }

    @Test
    fun `malformed callbacks are only acknowledged`() = runTest {
        press("garbage")
        assertEquals(listOf("answerCallbackQuery"), telegram.calls.map { it.method })
        press("sc:1:999")
        assertEquals("Операция уже удалена", answer())
    }
}
