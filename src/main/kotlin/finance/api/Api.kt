package finance.api

import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import finance.rates.RateUnavailableException
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.service.BudgetService
import finance.service.EntryService
import finance.service.ExportService
import finance.service.RecurringService
import finance.service.ReportService
import finance.telegram.TelegramApi
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

class ApiException(val status: HttpStatusCode, message: String) : RuntimeException(message)

class Api(
    val validator: InitDataValidator,
    val categories: CategoryRepository,
    val operations: OperationRepository,
    val entries: EntryService,
    val reports: ReportService,
    val budgets: BudgetService,
    val recurring: RecurringService,
    val export: ExportService,
    val telegram: TelegramApi,
    val ownerId: Long,
    val today: () -> LocalDate,
)

private val log = LoggerFactory.getLogger("finance.api")

private const val MAX_AMOUNT = 100_000_000_000L
private const val MAX_NOTE = 200

/** Mini App HTTP API under /api, plus /health. */
fun Application.financeApi(api: Api) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorDto(e.message ?: "error")) }
        exception<RateUnavailableException> { call, e ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorDto("Нет курса ${e.currency.code} на ${e.date}, попробуй позже"))
        }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("Некорректный запрос")) }
        exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "Некорректный запрос")) }
        exception<DateTimeParseException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("Некорректная дата")) }
        exception<Throwable> { call, e ->
            log.error("API error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Внутренняя ошибка"))
        }
    }
    routing {
        get("/health") { call.respondText("ok") }
        route("/api") {
            install(
                createRouteScopedPlugin("TelegramAuth") {
                    onCall { call ->
                        val initData = call.request.headers["Authorization"]?.removePrefix("tma ")
                        if (initData == null || !api.validator.isValid(initData)) {
                            throw ApiException(HttpStatusCode.Unauthorized, "Нет доступа")
                        }
                    }
                },
            )
            metaRoutes(api)
            operationRoutes(api)
            budgetRoutes(api)
            recurringRoutes(api)
            reportRoutes(api)
        }
    }
}

private fun Route.metaRoutes(api: Api) {
    get("/meta") {
        call.respond(
            MetaDto(
                baseCurrency = Currency.BASE.code,
                currencies = Currency.entries.map { it.code },
                today = api.today().toString(),
                categories = api.categories.all().map { (category, archived) -> category.toDto(archived) },
            ),
        )
    }
}

private fun Route.operationRoutes(api: Api) {
    route("/operations") {
        get {
            val from = call.dateParam("from")
            val to = call.dateParam("to")
            if (to.isBefore(from)) throw ApiException(HttpStatusCode.BadRequest, "Конец периода раньше начала")
            val categoryId = call.request.queryParameters["categoryId"]?.toLongOrNull()
            val type = call.request.queryParameters["type"]?.let { CategoryType.valueOf(it) }
            val currency = call.request.queryParameters["currency"]?.let { Currency.valueOf(it) }
            val query = call.request.queryParameters["q"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val result = api.operations.between(from, to).filter { op ->
                (categoryId == null || op.category.id == categoryId) &&
                    (type == null || op.category.type == type) &&
                    (currency == null || op.currency == currency) &&
                    (query == null || op.note.orEmpty().lowercase().contains(query))
            }
            call.respond(result.map { it.toDto() })
        }
        post {
            val (category, entry) = call.receive<OperationInput>().validate(api)
            call.respond(HttpStatusCode.Created, api.entries.save(entry, category).toDto())
        }
        put("/{id}") {
            val (category, entry) = call.receive<OperationInput>().validate(api)
            val updated = api.entries.update(call.idParam(), category, entry) ?: throw notFound()
            call.respond(updated.toDto())
        }
        delete("/{id}") {
            api.entries.delete(call.idParam()) ?: throw notFound()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Route.budgetRoutes(api: Api) {
    route("/budgets/{month}") {
        get {
            val month = call.monthParam()
            val today = api.today()
            val budgets = api.budgets.progress(month).map { p ->
                val forecast = if (YearMonth.from(today) == month) p.spentMinor * month.lengthOfMonth() / today.dayOfMonth else null
                BudgetDto(p.category.id, p.limitMinor, p.spentMinor, forecast)
            }
            call.respond(budgets)
        }
        put("/{categoryId}") {
            val month = call.monthParam()
            val category = api.expenseCategory(call.idParam("categoryId"))
            val limit = call.receive<BudgetInput>().limit
            if (limit <= 0 || limit > MAX_AMOUNT) throw ApiException(HttpStatusCode.BadRequest, "Лимит должен быть больше нуля")
            api.budgets.set(category, month, limit)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{categoryId}") {
            val category = api.expenseCategory(call.idParam("categoryId"))
            api.budgets.remove(category, call.monthParam())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/copy") {
            val month = call.monthParam()
            call.respond(CountDto(api.budgets.copy(month.minusMonths(1), month)))
        }
    }
}

private fun Route.recurringRoutes(api: Api) {
    route("/recurring") {
        get { call.respond(api.recurring.list().map { it.toDto() }) }
        post {
            val input = call.receive<RecurringInput>()
            val (category, currency) = input.validate(api)
            val created = api.recurring.create(input.name.trim(), category, input.amount, currency, input.schedule.toSchedule(), api.today())
            val result = if (input.paused) {
                api.recurring.update(created.id, created.name, category, created.amountMinor, currency, created.schedule, true, api.today())!!
            } else {
                created
            }
            call.respond(HttpStatusCode.Created, result.toDto())
        }
        put("/{id}") {
            val input = call.receive<RecurringInput>()
            val (category, currency) = input.validate(api)
            val updated = api.recurring.update(
                call.idParam(), input.name.trim(), category, input.amount, currency, input.schedule.toSchedule(), input.paused, api.today(),
            ) ?: throw notFound()
            call.respond(updated.toDto())
        }
        delete("/{id}") {
            if (!api.recurring.delete(call.idParam())) throw notFound()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Route.reportRoutes(api: Api) {
    get("/overview/{month}") {
        val month = call.monthParam()
        val summary = api.reports.summary(month.atDay(1), month.atEndOfMonth())
        val previous = month.minusMonths(1)
        val previousExpense = api.reports.summary(previous.atDay(1), previous.atEndOfMonth()).expenseMinor
        val days = summary.operations.groupBy { it.date }.toSortedMap().map { (date, ops) ->
            val (income, expense) = ops.partition { it.category.type == CategoryType.INCOME }
            DayTotalDto(date.toString(), expense.sumOf { it.amountBaseMinor }, income.sumOf { it.amountBaseMinor })
        }
        val today = api.today()
        call.respond(
            OverviewDto(
                month = month.toString(),
                income = summary.incomeMinor,
                expense = summary.expenseMinor,
                balance = summary.balanceMinor,
                previousExpense = previousExpense,
                expenseByCategory = summary.expenseByCategory.map { CategoryTotalDto(it.category.id, it.totalMinor) },
                days = days,
                budgets = api.budgets.progress(month).map { p ->
                    val forecast = if (YearMonth.from(today) == month) p.spentMinor * month.lengthOfMonth() / today.dayOfMonth else null
                    BudgetDto(p.category.id, p.limitMinor, p.spentMinor, forecast)
                },
            ),
        )
    }
    get("/reports/monthly") {
        val count = (call.request.queryParameters["months"]?.toIntOrNull() ?: 12).coerceIn(1, 36)
        val last = YearMonth.from(api.today())
        val first = last.minusMonths(count - 1L)
        val byMonth = api.operations.between(first.atDay(1), last.atEndOfMonth()).groupBy { YearMonth.from(it.date) }
        val result = (0 until count).map { offset ->
            val month = first.plusMonths(offset.toLong())
            val ops = byMonth[month].orEmpty()
            val (income, expense) = ops.partition { it.category.type == CategoryType.INCOME }
            MonthReportDto(
                month = month.toString(),
                income = income.sumOf { it.amountBaseMinor },
                expense = expense.sumOf { it.amountBaseMinor },
                expenseByCategory = expense.groupBy { it.category.id }
                    .map { (id, list) -> CategoryTotalDto(id, list.sumOf { it.amountBaseMinor }) }
                    .sortedByDescending { it.total },
            )
        }
        call.respond(result)
    }
    post("/export") {
        val input = call.receive<ExportInput>()
        val from = LocalDate.parse(input.from)
        val to = LocalDate.parse(input.to)
        if (to.isBefore(from)) throw ApiException(HttpStatusCode.BadRequest, "Конец периода раньше начала")
        val csv = api.export.csv(from, to)
        api.telegram.sendDocument(api.ownerId, "finance-$from-$to.csv", csv.toByteArray(), "📄 Операции с $from по $to")
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun OperationInput.validate(api: Api): Pair<Category, NewOperation> {
    val category = api.categories.findById(categoryId) ?: throw ApiException(HttpStatusCode.BadRequest, "Нет такой категории")
    if (amount <= 0 || amount > MAX_AMOUNT) throw ApiException(HttpStatusCode.BadRequest, "Сумма должна быть больше нуля")
    val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
    if ((cleanNote?.length ?: 0) > MAX_NOTE) throw ApiException(HttpStatusCode.BadRequest, "Слишком длинная заметка")
    return category to NewOperation(category.type, amount, currency(currency), LocalDate.parse(date), cleanNote)
}

private fun RecurringInput.validate(api: Api): Pair<Category, Currency> {
    if (name.isBlank() || name.length > MAX_NOTE) throw ApiException(HttpStatusCode.BadRequest, "Укажи название")
    if (amount <= 0 || amount > MAX_AMOUNT) throw ApiException(HttpStatusCode.BadRequest, "Сумма должна быть больше нуля")
    val category = api.categories.findById(categoryId) ?: throw ApiException(HttpStatusCode.BadRequest, "Нет такой категории")
    return category to currency(currency)
}

private fun Api.expenseCategory(id: Long): Category {
    val category = categories.findById(id) ?: throw notFound()
    if (category.type != CategoryType.EXPENSE) throw ApiException(HttpStatusCode.BadRequest, "Бюджет можно задать только для расходов")
    return category
}

private fun currency(code: String) =
    Currency.entries.firstOrNull { it.code == code } ?: throw ApiException(HttpStatusCode.BadRequest, "Неизвестная валюта $code")

private fun notFound() = ApiException(HttpStatusCode.NotFound, "Не найдено")

private fun ApplicationCall.idParam(name: String = "id"): Long =
    parameters[name]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "Некорректный id")

private fun ApplicationCall.monthParam(): YearMonth = try {
    YearMonth.parse(parameters["month"])
} catch (_: Exception) {
    throw ApiException(HttpStatusCode.BadRequest, "Некорректный месяц")
}

private fun ApplicationCall.dateParam(name: String): LocalDate =
    request.queryParameters[name]?.let(LocalDate::parse) ?: throw ApiException(HttpStatusCode.BadRequest, "Нужен параметр $name")
