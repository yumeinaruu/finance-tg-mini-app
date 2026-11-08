package finance

import finance.telegram.InlineKeyboardButton
import finance.telegram.InlineKeyboardMarkup
import finance.telegram.TelegramException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TelegramApiTest {

    @Test
    fun `sendMessage omits empty keyboard`() = runTest {
        val telegram = FakeTelegram()
        telegram.api.sendMessage(42, "привет")

        val call = telegram.last("sendMessage")
        assertEquals(42, call.body.getValue("chat_id").jsonPrimitive.long)
        assertEquals("привет", call.text)
        assertFalse("reply_markup" in call.body)
    }

    @Test
    fun `sendMessage sends inline keyboard`() = runTest {
        val telegram = FakeTelegram()
        val markup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("Да", "yes"), InlineKeyboardButton("Нет", "no"))))
        telegram.api.sendMessage(42, "?", markup)
        assertEquals(listOf("yes", "no"), telegram.last("sendMessage").buttons)
    }

    @Test
    fun `api errors are raised`() = runTest {
        val telegram = FakeTelegram { """{"ok":false,"description":"Bad Request: chat not found"}""" }
        val error = assertFailsWith<TelegramException> { telegram.api.sendMessage(1, "x") }
        assertEquals("Bad Request: chat not found", error.description)
    }

    @Test
    fun `not modified edits are ignored`() = runTest {
        val telegram = FakeTelegram {
            """{"ok":false,"description":"Bad Request: message is not modified: specified new message content is the same"}"""
        }
        telegram.api.editMessageText(1, 2, "same")
        telegram.api.editMessageReplyMarkup(1, 2, null)
        assertEquals(2, telegram.calls.size)
    }

    @Test
    fun `getUpdates parses messages and callbacks`() = runTest {
        val telegram = FakeTelegram {
            """
            {"ok":true,"result":[
              {"update_id":10,"message":{"message_id":5,"from":{"id":42,"is_bot":false},"chat":{"id":42,"type":"private"},"date":1,"text":"кофе 5"}},
              {"update_id":11,"callback_query":{"id":"cb","from":{"id":42},"message":{"message_id":6,"chat":{"id":42}},"data":"d:1"}}
            ]}
            """.trimIndent()
        }
        val updates = telegram.api.getUpdates(offset = 10, timeoutSeconds = 50)

        assertEquals("кофе 5", updates[0].message!!.text)
        assertEquals(42, updates[0].message!!.from!!.id)
        assertEquals("d:1", updates[1].callbackQuery!!.data)
        assertEquals(6, updates[1].callbackQuery!!.message!!.messageId)

        val body = telegram.last("getUpdates").body
        assertEquals(10, body.getValue("offset").jsonPrimitive.long)
        assertEquals(50, body.getValue("timeout").jsonPrimitive.long)
        assertEquals(listOf("message", "callback_query"), body.getValue("allowed_updates").jsonArray.map { it.jsonPrimitive.content })
    }
}
