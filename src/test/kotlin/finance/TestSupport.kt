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
import io.ktor.client.engine.mock.toByteArray
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
            "sendMessage", "sendDocument" -> """{"ok":true,"result":{"message_id":1,"chat":{"id":42}}}"""
            "getUpdates" -> """{"ok":true,"result":[]}"""
            else -> """{"ok":true,"result":true}"""
        }
    },
) {
    /** [body] is the JSON payload; multipart uploads have an empty [body] and the raw payload in [raw]. */
    data class Call(val method: String, val body: JsonObject, val raw: String = "") {
        val text: String get() = body.getValue("text").jsonPrimitive.content

        /** All callback_data values of the inline keyboard, row by row. */
        val buttons: List<String>
            get() = body["reply_markup"]?.jsonObject?.getValue("inline_keyboard")?.jsonArray
                ?.flatMap { row -> row.jsonArray.mapNotNull { it.jsonObject["callback_data"]?.jsonPrimitive?.content } }
                .orEmpty()
    }

    val calls = mutableListOf<Call>()

    val engine = MockEngine { request ->
        val method = request.url.encodedPath.substringAfterLast('/')
        val raw = request.body.toByteArray().decodeToString()
        val multipart = request.body.contentType?.contentType == "multipart"
        calls += Call(method, if (multipart) JsonObject(emptyMap()) else Json.parseToJsonElement(raw).jsonObject, raw)
        respondJson(responder(method))
    }

    val api = TelegramApi(HttpClient(engine) { configureDefaults() }, "TOKEN")

    fun calls(method: String) = calls.filter { it.method == method }

    fun last(method: String) = calls(method).last()

    fun clear() = calls.clear()
}

/** The whole app wired like in production, with fake Telegram, fake rates and a controllable clock. */
class TestApp(
    var now: java.time.LocalDateTime = LocalDate.of(2026, 11, 8).atTime(12, 0),
    rates: Map<Currency, BigDecimal>? = mapOf(
        Currency.USD to BigDecimal("3.000000"),
        Currency.EUR to BigDecimal("3.400000"),
        Currency.RUB to BigDecimal("0.036600"),
    ),
) {
    val chatId = 42L
    val telegram = FakeTelegram()
    val db = testDatabase()
    val today: () -> LocalDate = { now.toLocalDate() }
    val categoryRepository = finance.repo.CategoryRepository(db)
    val operations = finance.repo.OperationRepository(db)
    val budgetRepository = finance.repo.BudgetRepository(db)
    val recurringRepository = finance.repo.RecurringRepository(db)
    val rateService = finance.rates.RateService(finance.repo.RateRepository(db), listOf(FakeRateProvider(rates = rates)), today)
    val entries = finance.service.EntryService(categoryRepository, operations, finance.repo.PendingRepository(db), rateService)
    val categories = finance.service.CategoryService(categoryRepository, operations, budgetRepository, recurringRepository)
    val budgets = finance.service.BudgetService(budgetRepository, operations)
    val recurring = finance.service.RecurringService(recurringRepository, entries)
    val reports = finance.service.ReportService(operations)
    val conversation = finance.bot.Conversation()
    val backups = finance.service.BackupService(db)
    private val parser = finance.parse.EntryParser()

    val handler = finance.bot.BotHandler(
        telegram.api, parser, entries, reports, rateService, budgets,
        finance.bot.CategoryFlow(telegram.api, categories, entries, conversation, today),
        finance.bot.BudgetFlow(telegram.api, budgets, categories, today),
        finance.bot.RecurringFlow(telegram.api, recurring, categories, parser, today),
        backups, conversation, today, miniAppUrl = "https://finance.example/",
    )

    fun api(validator: finance.api.InitDataValidator) = finance.api.Api(
        validator, categoryRepository, categories, rateService, backups, operations, entries, reports, budgets, recurring,
        finance.service.ExportService(operations), telegram.api, chatId, today,
    )

    val scheduler = finance.bot.Scheduler(
        telegram.api, chatId, recurring, budgets, reports, finance.repo.StateRepository(db), backups, java.time.LocalTime.of(10, 0),
    ) { now }

    /** Sends a text message and returns the bot's reply. */
    suspend fun send(text: String): FakeTelegram.Call {
        telegram.clear()
        handler.onMessage(finance.telegram.Message(100, finance.telegram.User(chatId), finance.telegram.Chat(chatId), text))
        return telegram.last("sendMessage")
    }

    suspend fun press(data: String) {
        telegram.clear()
        handler.onCallback(
            finance.telegram.CallbackQuery("cb", finance.telegram.User(chatId), finance.telegram.Message(200, chat = finance.telegram.Chat(chatId)), data),
        )
    }

    /** Toast text of the last answered button. */
    fun answer(): String? = telegram.last("answerCallbackQuery").body["text"]?.jsonPrimitive?.content

    fun edited(): FakeTelegram.Call = telegram.last("editMessageText")

    fun categoryId(name: String): Long =
        finance.domain.CategoryType.entries.flatMap { categoryRepository.active(it) }.first { it.name == name }.id
}
