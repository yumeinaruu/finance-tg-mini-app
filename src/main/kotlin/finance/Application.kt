package finance

import finance.bot.Bot
import finance.bot.BotHandler
import finance.db.Database
import finance.db.Migrations
import finance.parse.EntryParser
import finance.rates.CurrencyApiProvider
import finance.rates.OpenErApiProvider
import finance.rates.RateService
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository
import finance.repo.RateRepository
import finance.service.EntryService
import finance.service.ReportService
import finance.telegram.TelegramApi
import finance.telegram.configureDefaults
import io.ktor.client.HttpClient
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import io.ktor.client.engine.cio.CIO as ClientCIO

private val log = LoggerFactory.getLogger("finance.Application")

fun main() {
    val config = Config.fromEnv()
    val db = Database(config.dbUrl)
    Migrations.apply(db)

    val http = HttpClient(ClientCIO) { configureDefaults() }

    val today = { LocalDate.now(config.zone) }
    val rates = RateService(RateRepository(db), listOf(CurrencyApiProvider(http), OpenErApiProvider(http)), today)
    val operations = OperationRepository(db)
    val entries = EntryService(CategoryRepository(db), operations, PendingRepository(db), rates)
    val api = TelegramApi(http, config.botToken)
    val handler = BotHandler(api, EntryParser(), entries, ReportService(operations), rates, today)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch { Bot(api, handler, config.ownerId).run() }
    scope.launch {
        while (true) {
            runCatching { rates.refreshToday() }.onFailure { log.warn("Rate refresh failed: {}", it.message) }
            delay(6.hours)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread { db.close() })

    embeddedServer(CIO, port = config.httpPort) {
        routing {
            get("/health") { call.respondText("ok") }
        }
    }.start(wait = true)
}
