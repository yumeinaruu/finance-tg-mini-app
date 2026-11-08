package finance

import finance.bot.Bot
import finance.bot.BotHandler
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
import finance.telegram.Update
import finance.telegram.User
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotTest {
    private val owner = 42L
    private val telegram = FakeTelegram()
    private val bot = run {
        val db = testDatabase()
        val today = { LocalDate.of(2026, 11, 8) }
        val rates = RateService(RateRepository(db), listOf(FakeRateProvider()), today)
        val operations = OperationRepository(db)
        val entries = EntryService(CategoryRepository(db), operations, PendingRepository(db), rates)
        Bot(telegram.api, BotHandler(telegram.api, EntryParser(), entries, ReportService(operations), rates, today), owner)
    }

    private fun message(from: Long, text: String) =
        Update(1, message = Message(1, User(from), Chat(from), text))

    @Test
    fun `owner messages are handled`() = runTest {
        bot.dispatch(message(owner, "/help"))
        assertTrue(telegram.last("sendMessage").text.startsWith("Привет!"))
    }

    @Test
    fun `strangers are ignored`() = runTest {
        bot.dispatch(message(7, "кофе 5"))
        bot.dispatch(Update(2, callbackQuery = CallbackQuery("cb", User(7), Message(1, chat = Chat(7)), "d:1")))

        assertEquals(listOf("answerCallbackQuery"), telegram.calls.map { it.method })
    }
}
