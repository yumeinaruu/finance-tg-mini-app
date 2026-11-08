package finance

import finance.bot.Bot
import finance.telegram.CallbackQuery
import finance.telegram.Chat
import finance.telegram.Message
import finance.telegram.Update
import finance.telegram.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotTest {
    private val app = TestApp()
    private val telegram = app.telegram
    private val bot = Bot(telegram.api, app.handler, ownerId = app.chatId)

    private fun message(from: Long, text: String) =
        Update(1, message = Message(1, User(from), Chat(from), text))

    @Test
    fun `owner messages are handled`() = runTest {
        bot.dispatch(message(app.chatId, "/help"))
        assertTrue(telegram.last("sendMessage").text.startsWith("Привет!"))
    }

    @Test
    fun `strangers are ignored`() = runTest {
        bot.dispatch(message(7, "кофе 5"))
        bot.dispatch(Update(2, callbackQuery = CallbackQuery("cb", User(7), Message(1, chat = Chat(7)), "d:1")))

        assertEquals(listOf("answerCallbackQuery"), telegram.calls.map { it.method })
    }
}
