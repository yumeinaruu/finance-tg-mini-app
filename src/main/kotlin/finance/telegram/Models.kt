package finance.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TgResponse<T>(val ok: Boolean, val result: T? = null, val description: String? = null)

@Serializable
data class Update(
    @SerialName("update_id") val updateId: Long,
    val message: Message? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null,
)

@Serializable
data class Message(
    @SerialName("message_id") val messageId: Long,
    val from: User? = null,
    val chat: Chat,
    val text: String? = null,
)

@Serializable
data class User(val id: Long)

@Serializable
data class Chat(val id: Long)

@Serializable
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null,
)

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String,
)

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>,
)

@Serializable
data class BotCommand(val command: String, val description: String)

@Serializable
internal data class GetUpdatesRequest(
    val offset: Long?,
    val timeout: Int,
    @SerialName("allowed_updates") val allowedUpdates: List<String>,
)

@Serializable
internal data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
)

@Serializable
internal data class EditMessageTextRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("message_id") val messageId: Long,
    val text: String,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
)

@Serializable
internal data class EditMessageReplyMarkupRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("message_id") val messageId: Long,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
)

@Serializable
internal data class AnswerCallbackQueryRequest(
    @SerialName("callback_query_id") val callbackQueryId: String,
    val text: String? = null,
)

@Serializable
internal data class SetMyCommandsRequest(val commands: List<BotCommand>)
