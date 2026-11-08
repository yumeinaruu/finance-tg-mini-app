package finance

import finance.api.InitDataValidator
import finance.api.financeApi
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {
    private val app = TestApp()
    private val token = "123:secret"
    private val auth = "tma " + signedInitData(token, app.chatId)

    private fun test(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { financeApi(app.api(InitDataValidator(token, app.chatId) { Instant.parse("2026-11-08T13:00:00Z") })) }
        block(client)
    }

    private suspend fun HttpClient.call(method: String, path: String, body: String? = null): HttpResponse {
        val builder: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header("Authorization", auth)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (method) {
            "GET" -> get(path, builder)
            "POST" -> post(path, builder)
            "PUT" -> put(path, builder)
            "DELETE" -> delete(path, builder)
            else -> error(method)
        }
    }

    private suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

    private val JsonElement.obj: JsonObject get() = jsonObject
    private val JsonElement.arr: JsonArray get() = jsonArray
    private fun JsonElement.str(key: String) = jsonObject.getValue(key).jsonPrimitive.content
    private fun JsonElement.num(key: String) = jsonObject.getValue(key).jsonPrimitive.long

    private fun food() = app.categoryId("Еда")

    @Test
    fun `api requires valid launch data, health does not`() = test { client ->
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/meta").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/meta") { header("Authorization", "tma " + signedInitData(token, 7)) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/meta") { header("Authorization", "tma " + signedInitData("x:y", app.chatId)) }.status)
        assertEquals("Нет доступа", client.get("/api/meta").json().str("error"))
    }

    @Test
    fun `meta lists categories including hidden ones`() = test { client ->
        app.categoryRepository.setArchived(app.categoryId("Подарки"), true)
        val meta = client.call("GET", "/api/meta").json()
        assertEquals("BYN", meta.str("baseCurrency"))
        assertEquals("2026-11-08", meta.str("today"))
        assertEquals(listOf("BYN", "USD", "EUR", "RUB"), meta.obj.getValue("currencies").arr.map { it.jsonPrimitive.content })
        val gifts = meta.obj.getValue("categories").arr.first { it.str("name") == "Подарки" }
        assertEquals("true", gifts.str("archived"))
        assertEquals(11, meta.obj.getValue("categories").arr.size)
    }

    @Test
    fun `operations crud with conversion`() = test { client ->
        val created = client.call("POST", "/api/operations", """{"categoryId":${food()},"amount":2000,"currency":"USD","date":"2026-11-05","note":" обед "}""")
        assertEquals(HttpStatusCode.Created, created.status)
        val op = created.json()
        assertEquals(6000, op.num("amountBase"))
        assertEquals("обед", op.str("note"))
        assertEquals("EXPENSE", op.str("type"))
        val id = op.num("id")

        val updated = client.call("PUT", "/api/operations/$id", """{"categoryId":${app.categoryId("Транспорт")},"amount":1500,"currency":"BYN","date":"2026-11-06"}""").json()
        assertEquals(1500, updated.num("amountBase"))
        assertEquals("1", updated.str("rate"))
        assertEquals("2026-11-06", updated.str("date"))
        assertTrue(updated.obj.getValue("note").toString() == "null")

        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/operations/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.call("DELETE", "/api/operations/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.call("PUT", "/api/operations/$id", """{"categoryId":${food()},"amount":1,"currency":"BYN","date":"2026-11-06"}""").status)
    }

    @Test
    fun `operations are filtered`() = test { client ->
        app.send("кофе 5")
        app.send("такси 20 usd")
        app.send("+1500 зп")
        app.send("обед 12 01.10")

        suspend fun notes(query: String) =
            client.call("GET", "/api/operations?from=2026-11-01&to=2026-11-30$query").json().arr.map { it.str("note") }

        assertEquals(listOf("кофе", "такси", "зп"), notes(""))
        assertEquals(listOf("кофе"), notes("&categoryId=${food()}"))
        assertEquals(listOf("зп"), notes("&type=INCOME"))
        assertEquals(listOf("такси"), notes("&currency=USD"))
        assertEquals(listOf("такси"), notes("&q=ТАКС"))
        assertEquals(HttpStatusCode.BadRequest, client.call("GET", "/api/operations?from=2026-11-30&to=2026-11-01").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("GET", "/api/operations?from=2026-11-01").status)
    }

    @Test
    fun `invalid operations are rejected with a message`() = test { client ->
        suspend fun error(body: String): String {
            val response = client.call("POST", "/api/operations", body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            return response.json().str("error")
        }
        assertEquals("Сумма должна быть больше нуля", error("""{"categoryId":${food()},"amount":0,"currency":"BYN","date":"2026-11-05"}"""))
        assertEquals("Неизвестная валюта XYZ", error("""{"categoryId":${food()},"amount":1,"currency":"XYZ","date":"2026-11-05"}"""))
        assertEquals("Нет такой категории", error("""{"categoryId":999,"amount":1,"currency":"BYN","date":"2026-11-05"}"""))
        assertEquals("Некорректная дата", error("""{"categoryId":${food()},"amount":1,"currency":"BYN","date":"05.11.2026"}"""))
        assertEquals("Некорректный запрос", error("""{"amount":1}"""))
        assertEquals("Слишком длинная заметка", error("""{"categoryId":${food()},"amount":1,"currency":"BYN","date":"2026-11-05","note":"${"x".repeat(201)}"}"""))
    }

    @Test
    fun `missing rate is a 503`() {
        val noRates = TestApp(rates = null)
        testApplication {
            application { financeApi(noRates.api(InitDataValidator(token, noRates.chatId) { Instant.parse("2026-11-08T13:00:00Z") })) }
            val response = client.call("POST", "/api/operations", """{"categoryId":${noRates.categoryId("Еда")},"amount":100,"currency":"USD","date":"2026-11-05"}""")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("Нет курса USD на 2026-11-05, попробуй позже", response.json().str("error"))
        }
    }

    @Test
    fun `budgets with forecast and copy`() = test { client ->
        app.send("кофе 80")
        assertEquals(HttpStatusCode.NoContent, client.call("PUT", "/api/budgets/2026-11/${food()}", """{"limit":60000}""").status)
        val budget = client.call("GET", "/api/budgets/2026-11").json().arr.single()
        assertEquals(60000, budget.num("limit"))
        assertEquals(8000, budget.num("spent"))
        assertEquals(30000, budget.num("forecast"), "80 BYN in 8 days -> 300 BYN in 30 days")

        assertEquals(1, client.call("POST", "/api/budgets/2026-12/copy").json().obj.getValue("count").jsonPrimitive.int)
        assertTrue(client.call("GET", "/api/budgets/2026-12").json().arr.single().obj.getValue("forecast").toString() == "null")

        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/budgets/2026-11/${food()}").status)
        assertEquals(0, client.call("GET", "/api/budgets/2026-11").json().arr.size)

        assertEquals(HttpStatusCode.BadRequest, client.call("PUT", "/api/budgets/2026-11/${app.categoryId("Зарплата")}", """{"limit":100}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("PUT", "/api/budgets/2026-11/${food()}", """{"limit":0}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("GET", "/api/budgets/ноябрь").status)
        assertEquals(HttpStatusCode.NotFound, client.call("PUT", "/api/budgets/2026-11/999", """{"limit":100}""").status)
    }

    @Test
    fun `recurring crud and pause`() = test { client ->
        val subs = app.categoryId("Подписки и связь")
        val created = client.call(
            "POST", "/api/recurring",
            """{"name":"Netflix","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"monthly","day":15}}""",
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val id = created.json().num("id")
        assertEquals("2026-11-15", created.json().str("nextRun"))
        assertEquals("каждый месяц 15-го", created.json().str("description"))

        val paused = client.call(
            "PUT", "/api/recurring/$id",
            """{"name":"Netflix","categoryId":$subs,"amount":1200,"currency":"USD","schedule":{"kind":"monthly","day":15},"paused":true}""",
        ).json()
        assertEquals("true", paused.str("paused"))
        assertEquals(1200, paused.num("amount"))

        app.now = LocalDate.of(2027, 1, 20).atTime(12, 0)
        assertEquals(emptyList(), app.recurring.processDue(app.now, java.time.LocalTime.of(10, 0)), "paused payments are skipped")

        val resumed = client.call(
            "PUT", "/api/recurring/$id",
            """{"name":"Netflix","categoryId":$subs,"amount":1200,"currency":"USD","schedule":{"kind":"monthly","day":15},"paused":false}""",
        ).json()
        assertEquals("2027-02-15", resumed.str("nextRun"), "dates missed during the pause are skipped")

        val weekly = client.call(
            "PUT", "/api/recurring/$id",
            """{"name":"Netflix","categoryId":$subs,"amount":1200,"currency":"USD","schedule":{"kind":"weekly","weekday":1}}""",
        ).json()
        assertEquals("по понедельникам", weekly.str("description"))
        assertEquals("2027-01-25", weekly.str("nextRun"))

        assertEquals(1, client.call("GET", "/api/recurring").json().arr.size)
        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/recurring/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.call("DELETE", "/api/recurring/$id").status)
    }

    @Test
    fun `invalid recurring payments are rejected`() = test { client ->
        val subs = app.categoryId("Подписки и связь")
        listOf(
            """{"name":"","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"monthly","day":15}}""",
            """{"name":"X","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"daily"}}""",
            """{"name":"X","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"weekly"}}""",
            """{"name":"X","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"monthly","day":40}}""",
            """{"name":"X","categoryId":$subs,"amount":1000,"currency":"USD","schedule":{"kind":"yearly","month":2,"day":30}}""",
        ).forEach { assertEquals(HttpStatusCode.BadRequest, client.call("POST", "/api/recurring", it).status, it) }
    }

    @Test
    fun `overview and monthly report`() = test { client ->
        app.send("кофе 5")
        app.send("такси 20 usd 07.11")
        app.send("+1500 зп")
        app.send("кино 30 15.10")
        app.send("/budget еда 100")

        val overview = client.call("GET", "/api/overview/2026-11").json()
        assertEquals(150000, overview.num("income"))
        assertEquals(6500, overview.num("expense"))
        assertEquals(143500, overview.num("balance"))
        assertEquals(3000, overview.num("previousExpense"))
        assertEquals(listOf(6000L, 500L), overview.obj.getValue("expenseByCategory").arr.map { it.num("total") })
        assertEquals(listOf("2026-11-07", "2026-11-08"), overview.obj.getValue("days").arr.map { it.str("date") })
        assertEquals(150000, overview.obj.getValue("days").arr.last().num("income"))
        assertEquals(500, overview.obj.getValue("budgets").arr.single().num("spent"))

        val report = client.call("GET", "/api/reports/monthly?months=3").json().arr
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), report.map { it.str("month") })
        assertEquals(listOf(0L, 3000L, 6500L), report.map { it.num("expense") })
        assertEquals(app.categoryId("Развлечения"), report[1].obj.getValue("expenseByCategory").arr.single().num("categoryId"))
    }

    @Test
    fun `export sends csv to the chat`() = test { client ->
        app.send("кофе 5")
        app.send("обед; с другом 12,5")
        app.telegram.clear()
        assertEquals(HttpStatusCode.NoContent, client.call("POST", "/api/export", """{"from":"2026-11-01","to":"2026-11-30"}""").status)

        val upload = app.telegram.last("sendDocument").raw
        assertTrue("filename=\"finance-2026-11-01-2026-11-30.csv\"" in upload)
        assertTrue("date;type;category;amount;currency;rate;amount_byn;note" in upload)
        assertTrue("2026-11-08;expense;Еда;5.00;BYN;1;5.00;кофе" in upload)
        assertTrue("\"обед; с другом\"" in upload, "fields with separators are quoted")
    }

    @Test
    fun `categories are listed with words and usage`() = test { client ->
        app.send("кофе 5")
        val food = client.call("GET", "/api/categories").json().arr.first { it.str("name") == "Еда" }
        assertEquals(1, food.num("operations"))
        assertEquals(500, food.num("total"))
        assertTrue("кофе" in food.obj.getValue("aliases").arr.map { it.jsonPrimitive.content })
        assertEquals(11, client.call("GET", "/api/categories").json().arr.size)
    }

    @Test
    fun `category is created, renamed and restored`() = test { client ->
        val created = client.call("POST", "/api/categories", """{"name":"  спорт  ","type":"EXPENSE"}""")
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("Спорт", created.json().str("name"))
        val id = created.json().num("id")

        val conflict = client.call("POST", "/api/categories", """{"name":"спорт","type":"EXPENSE"}""")
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("Категория «Спорт» уже есть", conflict.json().str("error"))
        assertEquals(HttpStatusCode.Created, client.call("POST", "/api/categories", """{"name":"Спорт","type":"INCOME"}""").status, "names are unique per type")

        assertEquals("Фитнес", client.call("PUT", "/api/categories/$id", """{"name":"фитнес"}""").json().str("name"))
        assertEquals(HttpStatusCode.Conflict, client.call("PUT", "/api/categories/$id", """{"name":"Еда"}""").status)

        app.send("фитнес 30")
        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/categories/$id?hide=true").status)
        assertEquals(HttpStatusCode.OK, client.call("POST", "/api/categories", """{"name":"Фитнес","type":"EXPENSE"}""").status, "hidden category is restored")

        assertEquals(HttpStatusCode.BadRequest, client.call("POST", "/api/categories", """{"name":" ","type":"EXPENSE"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("POST", "/api/categories", """{"name":"${"а".repeat(33)}","type":"EXPENSE"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("POST", "/api/categories", """{"name":"X","type":"OTHER"}""").status)
    }

    @Test
    fun `used category must be moved or hidden before deletion`() = test { client ->
        app.send("кино 20")
        val fun_ = app.categoryId("Развлечения")
        val gifts = app.categoryId("Подарки")

        assertEquals(HttpStatusCode.Conflict, client.call("DELETE", "/api/categories/$fun_").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("DELETE", "/api/categories/$fun_?moveTo=${app.categoryId("Зарплата")}").status, "different type")
        assertEquals(HttpStatusCode.BadRequest, client.call("DELETE", "/api/categories/$fun_?moveTo=$fun_").status, "same category")

        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/categories/$fun_?moveTo=$gifts").status)
        assertEquals(HttpStatusCode.NotFound, client.call("DELETE", "/api/categories/$fun_").status)
        assertTrue(app.send("кино 5").text.startsWith("🎁 Подарки"), "operations and words moved")

        val unused = app.categoryId("Прочий доход")
        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/categories/$unused").status, "unused category is deleted right away")
    }

    @Test
    fun `words are added and removed`() = test { client ->
        val health = app.categoryId("Здоровье")
        assertEquals("бассейн", client.call("POST", "/api/categories/$health/aliases", """{"word":" Бассейн "}""").json().str("alias"))
        assertTrue(app.send("бассейн 15").text.startsWith("💊 Здоровье"))
        assertEquals(HttpStatusCode.NoContent, client.call("DELETE", "/api/categories/$health/aliases/бассейн").status)
        assertEquals(HttpStatusCode.NotFound, client.call("DELETE", "/api/categories/$health/aliases/бассейн").status)
        assertEquals(HttpStatusCode.BadRequest, client.call("POST", "/api/categories/$health/aliases", """{"word":"!!!"}""").status)
    }

    @Test
    fun `rates and backup`() = test { client ->
        val rates = client.call("GET", "/api/rates").json()
        assertEquals("2026-11-08", rates.str("date"))
        val rub = rates.obj.getValue("rates").arr.first { it.str("currency") == "RUB" }
        assertEquals("0.036600", rub.str("rate"))
        assertEquals(100, rub.num("unit"))

        app.send("кофе 5")
        app.telegram.clear()
        assertEquals(HttpStatusCode.NoContent, client.call("POST", "/api/backup").status)
        val upload = app.telegram.last("sendDocument").raw
        assertTrue("finance-2026-11-08.db" in upload)
        assertTrue("SQLite format 3" in upload)
    }
}
