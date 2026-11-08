package finance

import finance.db.Database
import finance.db.Migrations
import finance.domain.Currency
import finance.rates.RateProvider
import finance.rates.RateSnapshot
import finance.telegram.TelegramApi
import finance.telegram.configureDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

fun testDatabase(): Database = Database("jdbc:sqlite::memory:").also(Migrations::apply)

class FakeRateProvider(
    override val name: String = "fake",
    private val rates: Map<Currency, BigDecimal>? = mapOf(Currency.USD to BigDecimal("3.000000")),
) : RateProvider {
    val requests = mutableListOf<LocalDate?>()

    override suspend fun fetch(date: LocalDate?): RateSnapshot? {
        requests += date
        return rates?.let { RateSnapshot(date ?: LocalDate.of(2026, 11, 8), it) }
    }
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun HttpRequestData.bodyText(): String = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

/** Records Bot API calls and answers them like Telegram would. */
class FakeTelegram(
    private val responder: (method: String) -> String = { method ->
        when (method) {
            "sendMessage" -> """{"ok":true,"result":{"message_id":1,"chat":{"id":42}}}"""
            "getUpdates" -> """{"ok":true,"result":[]}"""
            else -> """{"ok":true,"result":true}"""
        }
    },
) {
    data class Call(val method: String, val body: JsonObject) {
        val text: String get() = body.getValue("text").jsonPrimitive.content

        /** All callback_data values of the inline keyboard, row by row. */
        val buttons: List<String>
            get() = body["reply_markup"]?.jsonObject?.getValue("inline_keyboard")?.jsonArray
                ?.flatMap { row -> row.jsonArray.map { it.jsonObject.getValue("callback_data").jsonPrimitive.content } }
                .orEmpty()
    }

    val calls = mutableListOf<Call>()

    val engine = MockEngine { request ->
        val method = request.url.encodedPath.substringAfterLast('/')
        calls += Call(method, Json.parseToJsonElement(request.bodyText()).jsonObject)
        respondJson(responder(method))
    }

    val api = TelegramApi(HttpClient(engine) { configureDefaults() }, "TOKEN")

    fun calls(method: String) = calls.filter { it.method == method }

    fun last(method: String) = calls(method).last()

    fun clear() = calls.clear()
}
