package finance.telegram

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement

class TelegramException(method: String, val description: String?) :
    RuntimeException("Telegram $method failed: $description") {
    val isNotModified: Boolean get() = description?.contains("message is not modified") == true
}

/** Thin client for the few Bot API methods the bot needs. */
class TelegramApi(private val http: HttpClient, token: String) {
    private val baseUrl = "https://api.telegram.org/bot$token"

    suspend fun getUpdates(offset: Long?, timeoutSeconds: Int): List<Update> =
        call("getUpdates", GetUpdatesRequest(offset, timeoutSeconds, listOf("message", "callback_query")), timeoutSeconds)

    suspend fun sendMessage(chatId: Long, text: String, markup: InlineKeyboardMarkup? = null) {
        call<Message, SendMessageRequest>("sendMessage", SendMessageRequest(chatId, text, markup))
    }

    suspend fun editMessageText(chatId: Long, messageId: Long, text: String, markup: InlineKeyboardMarkup? = null) =
        ignoreNotModified {
            call<JsonElement, EditMessageTextRequest>("editMessageText", EditMessageTextRequest(chatId, messageId, text, markup))
        }

    suspend fun editMessageReplyMarkup(chatId: Long, messageId: Long, markup: InlineKeyboardMarkup?) =
        ignoreNotModified {
            call<JsonElement, EditMessageReplyMarkupRequest>(
                "editMessageReplyMarkup",
                EditMessageReplyMarkupRequest(chatId, messageId, markup),
            )
        }

    suspend fun answerCallbackQuery(id: String, text: String? = null) {
        call<Boolean, AnswerCallbackQueryRequest>("answerCallbackQuery", AnswerCallbackQueryRequest(id, text))
    }

    suspend fun setMyCommands(commands: List<BotCommand>) {
        call<Boolean, SetMyCommandsRequest>("setMyCommands", SetMyCommandsRequest(commands))
    }

    private suspend inline fun ignoreNotModified(block: () -> Unit) {
        try {
            block()
        } catch (e: TelegramException) {
            if (!e.isNotModified) throw e
        }
    }

    private suspend inline fun <reified T, reified B> call(method: String, body: B, longPollSeconds: Int = 0): T {
        val response: TgResponse<T> = http.post("$baseUrl/$method") {
            contentType(ContentType.Application.Json)
            setBody(body)
            timeout { requestTimeoutMillis = (longPollSeconds + 20) * 1000L }
        }.body()
        if (!response.ok) throw TelegramException(method, response.description)
        return response.result ?: throw TelegramException(method, "empty result")
    }
}
