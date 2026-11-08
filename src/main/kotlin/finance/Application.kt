package finance

import finance.bot.Bot
import finance.bot.BotHandler
import finance.bot.BudgetFlow
import finance.bot.CategoryFlow
import finance.bot.Conversation
import finance.bot.RecurringFlow
import finance.bot.Scheduler
import finance.db.Database
import finance.db.Migrations
import finance.parse.EntryParser
import finance.rates.CurrencyApiProvider
import finance.rates.OpenErApiProvider
import finance.rates.RateService
import finance.repo.BudgetRepository
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository
import finance.repo.RateRepository
import finance.repo.RecurringRepository
import finance.repo.StateRepository
import finance.service.BudgetService
import finance.service.CategoryService
import finance.service.EntryService
import finance.service.RecurringService
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
import java.time.LocalDateTime
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
    val categoryRepository = CategoryRepository(db)
    val operations = OperationRepository(db)
    val recurringRepository = RecurringRepository(db)
    val budgetRepository = BudgetRepository(db)
    val entries = EntryService(categoryRepository, operations, PendingRepository(db), rates)
    val categories = CategoryService(categoryRepository, operations, budgetRepository, recurringRepository)
    val budgets = BudgetService(budgetRepository, operations)
    val recurring = RecurringService(recurringRepository, entries)
    val reports = ReportService(operations)
    val api = TelegramApi(http, config.botToken)
    val parser = EntryParser()
    val conversation = Conversation()
    val handler = BotHandler(
        api, parser, entries, reports, rates, budgets,
        CategoryFlow(api, categories, entries, conversation, today),
        BudgetFlow(api, budgets, categories, today),
        RecurringFlow(api, recurring, categories, parser, today),
        conversation, today,
    )
    val scheduler = Scheduler(api, config.ownerId, recurring, budgets, reports, StateRepository(db), config.notifyAt) {
        LocalDateTime.now(config.zone)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch { Bot(api, handler, config.ownerId).run() }
    scope.launch { scheduler.run() }
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
