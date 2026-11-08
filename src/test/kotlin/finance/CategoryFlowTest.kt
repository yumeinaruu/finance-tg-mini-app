package finance

import finance.bot.Awaiting
import finance.domain.CategoryType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryFlowTest {
    private val app = TestApp()

    private fun activeNames(type: CategoryType = CategoryType.EXPENSE) = app.categoryRepository.active(type).map { it.name }

    @Test
    fun `categories overview with aliases and menu`() = runTest {
        val reply = app.send("/categories")
        assertTrue(reply.text.startsWith("📂 Категории\n\nРасходы:\n🍔 Еда — кофе, кафе, обед, ужин, завтрак, продукты, магазин, шаурма, …"))
        assertTrue("\nДоходы:\n💰 Зарплата — зп, аванс, премия\n" in reply.text)
        assertEquals(listOf("ca:0", "ca:1", "cd:0"), reply.buttons)
        assertEquals(reply.text, app.send("/cat").text)
    }

    @Test
    fun `add expense and income categories`() = runTest {
        assertEquals("✅ Добавил категорию расходов «🏋️ Спорт»", app.send("/cat add спорт 🏋️").text)
        assertEquals("✅ Добавил категорию доходов «💸 Кэшбэк бонусы»", app.send("/cat add +Кэшбэк бонусы 💸").text)
        assertEquals("✅ Добавил категорию расходов «📁 Хобби»", app.send("/cat add Хобби").text)
        assertEquals("Категория «🏋️ Спорт» уже есть", app.send("/cat add Спорт").text)

        assertEquals("Спорт", activeNames().dropLast(1).last())
        assertTrue("Кэшбэк бонусы" in activeNames(CategoryType.INCOME))
        assertTrue(app.send("спорт 30").text.startsWith("🏋️ Спорт  −30.00 BYN"), "new category is matched by name")
    }

    @Test
    fun `invalid add shows help`() = runTest {
        assertTrue(app.send("/cat add").text.startsWith("Управление категориями"))
        assertTrue(app.send("/cat add ${"а".repeat(33)}").text.startsWith("Слишком длинное название"))
        assertTrue(app.send("/cat foo").text.startsWith("Управление категориями"))
    }

    @Test
    fun `rename keeps operations and supports multi-word names`() = runTest {
        app.send("netflix 10")
        assertEquals("✏️ «📱 Подписки и связь» → «📺 Подписки»", app.send("/cat rename Подписки и связь Подписки 📺").text)
        assertEquals("✏️ «📺 Подписки» → «🎬 Подписки»", app.send("/cat rename подписки 🎬").text)
        assertTrue(app.send("/today").text.contains("🎬 Подписки −10.00 BYN · netflix"))

        assertEquals("Категория «Еда» уже есть", app.send("/cat rename Транспорт Еда").text)
        assertTrue(app.send("/cat rename Несуществующая Новая").text.startsWith("Не нашёл, что переименовать"))
    }

    @Test
    fun `unused category is deleted right away`() = runTest {
        app.send("/cat add Спорт")
        app.send("/alias бассейн спорт")
        assertEquals("🗑 Удалил категорию «📁 Спорт»", app.send("/cat del спорт").text)
        assertFalse("Спорт" in activeNames())
        assertNull(app.categoryRepository.aliases()["бассейн"])
        assertEquals("Не нашёл категорию «спорт». Список: /categories", app.send("/cat del спорт").text)
    }

    @Test
    fun `used category can be hidden`() = runTest {
        app.send("кино 20")
        val question = app.send("/cat del развлечения")
        assertEquals("В «🎉 Развлечения» 1 операция на 20.00 BYN. Что с ними сделать?", question.text)
        val id = app.categoryId("Развлечения")
        assertEquals(listOf("cm:$id", "ch:$id", "cn:$id"), question.buttons)

        app.press("ch:$id")
        assertEquals("🗄 Категория «🎉 Развлечения» скрыта, операции остались в отчётах", app.edited().text)
        assertFalse("Развлечения" in activeNames())
        assertTrue(app.send("/month").text.contains("🎉 Развлечения  20.00"), "hidden category stays in reports")
        assertTrue(app.send("кино 5").text.startsWith("Не знаю категорию"), "aliases of hidden categories are ignored")

        assertEquals("♻️ Вернул из архива категорию «🎉 Развлечения»", app.send("/cat add развлечения").text)
        assertTrue(app.send("кино 5").text.startsWith("🎉 Развлечения"))
    }

    @Test
    fun `used category can be moved into another`() = runTest {
        app.send("кино 20")
        app.send("/recurring add Кинотеатр 15 пт")
        app.press("rc:1:${app.categoryId("Развлечения")}")
        val id = app.categoryId("Развлечения")
        val gifts = app.categoryId("Подарки")

        assertEquals(
            "В «🎉 Развлечения» 1 операция на 20.00 BYN и 1 регулярный платёж. Что с ними сделать?",
            app.send("/cat del Развлечения").text,
        )
        app.press("cm:$id")
        assertEquals("Куда перенести операции из «🎉 Развлечения»?", app.edited().text)
        assertFalse("ct:$id:$id" in app.edited().buttons)
        assertTrue("ct:$id:$gifts" in app.edited().buttons)

        app.press("ct:$id:$gifts")
        assertEquals("↪️ Операции перенесены в «🎁 Подарки», категория «🎉 Развлечения» удалена", app.edited().text)
        assertNull(app.categoryRepository.findById(id))
        assertTrue(app.send("/today").text.contains("🎁 Подарки −20.00 BYN · кино"))
        assertTrue(app.send("кино 5").text.startsWith("🎁 Подарки"), "aliases are moved")
        assertTrue(app.send("/recurring").text.contains("Кинотеатр — 15.00 BYN · 🎁 Подарки"))

        app.press("ct:$id:$gifts")
        assertEquals("Категория уже удалена", app.answer())
    }

    @Test
    fun `delete via buttons and cancel`() = runTest {
        val menu = app.send("/categories")
        assertTrue("cd:0" in menu.buttons)
        app.press("cd:0")
        val choose = app.telegram.last("sendMessage")
        assertEquals("Какую категорию удалить?", choose.text)
        assertTrue("cx:${app.categoryId("Прочий доход")}" in choose.buttons)

        app.press("cx:${app.categoryId("Прочий доход")}")
        assertEquals("🗑 Удалил категорию «➕ Прочий доход»", app.edited().text)

        app.send("кофе 5")
        app.press("cx:${app.categoryId("Еда")}")
        app.press("cn:${app.categoryId("Еда")}")
        assertEquals("Отменено", app.edited().text)
        assertTrue("Еда" in activeNames())
    }

    @Test
    fun `aliases are added and removed`() = runTest {
        assertEquals("🔗 «бассейн» → 💊 Здоровье", app.send("/alias Бассейн здоровье").text)
        assertTrue(app.send("бассейн 15").text.startsWith("💊 Здоровье"))
        assertEquals("🔗 «шаурма» → 🎁 Подарки", app.send("/alias шаурма подарки").text)
        assertTrue(app.send("шаурма 8").text.startsWith("🎁 Подарки"), "alias is moved to another category")

        assertEquals("Отвязал слово «бассейн»", app.send("/alias del бассейн").text)
        assertEquals("Слово «бассейн» ни к чему не привязано", app.send("/alias del бассейн").text)
        assertTrue(app.send("бассейн 15").text.startsWith("Не знаю категорию"))

        assertEquals("Не нашёл категорию «космос». Список: /categories", app.send("/alias ракета космос").text)
        assertTrue(app.send("/alias").text.startsWith("📂 Категории"))
    }

    @Test
    fun `category is found by alias and unique prefix`() = runTest {
        assertEquals("🔗 «пицца» → 🍔 Еда", app.send("/alias пицца кофе").text)
        assertEquals("🔗 «вайфай» → 📱 Подписки и связь", app.send("/alias вайфай подписки").text)
        assertEquals("Не нашёл категорию «п». Список: /categories", app.send("/alias слово п").text, "ambiguous prefix")
    }

    @Test
    fun `new category from add button`() = runTest {
        app.send("/categories")
        app.press("ca:1")
        assertEquals(Awaiting.CategoryName(CategoryType.INCOME, null), app.conversation.awaiting)
        assertTrue(app.telegram.last("sendMessage").text.startsWith("Напиши название новой категории доходов"))

        assertEquals("✅ Добавил категорию доходов «💸 Кэшбэк»", app.send("кэшбэк 💸").text)
        assertNull(app.conversation.awaiting)
    }

    @Test
    fun `new category for an unknown word`() = runTest {
        app.send("бассейн 15")
        app.press("pn:1")
        assertEquals(
            "Не знаю категорию для «бассейн» — −15.00 BYN. Куда записать?\n\n" +
                "Напиши название новой категории расходов, можно с emoji, например «Спорт 🏋️».\n/cancel — отмена",
            app.edited().text,
        )

        val reply = app.send("Спорт 🏊")
        assertEquals("✅ Добавил категорию расходов «🏊 Спорт»\n\n🏊 Спорт  −15.00 BYN\n📅 сегодня · бассейн", reply.text)
        assertEquals(listOf("cc:1", "d:1"), reply.buttons)
        assertTrue(app.send("бассейн 10").text.startsWith("🏊 Спорт"), "word is remembered")

        app.press("pn:1")
        assertEquals("Эта запись уже обработана", app.edited().text)
    }

    @Test
    fun `waiting for input is cancelled by any command`() = runTest {
        app.press("ca:0")
        assertEquals("Ок, отменил", app.send("/cancel").text)
        assertEquals("Нечего отменять", app.send("/cancel").text)
        assertTrue(app.send("кофе 5").text.startsWith("🍔 Еда"), "plain text is an operation again")

        app.press("ca:0")
        app.send("/today")
        assertNull(app.conversation.awaiting)
    }
}
