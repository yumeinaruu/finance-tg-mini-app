package finance

import finance.db.Database
import finance.db.query
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupTest {
    private val app = TestApp(now = LocalDateTime.of(2026, 11, 8, 12, 0))

    private suspend fun tickAt(time: String): List<FakeTelegram.Call> {
        app.now = LocalDateTime.parse(time)
        app.telegram.clear()
        app.scheduler.tick()
        return app.telegram.calls("sendDocument")
    }

    @Test
    fun `backup is a working copy of the database`() = runTest {
        app.send("кофе 5")
        app.send("/budget еда 600")
        val backup = app.backups.create(LocalDate.of(2026, 11, 8))
        assertEquals("finance-2026-11-08.db", backup.fileName)
        assertEquals(1, backup.operations)

        val file = Files.createTempFile("restore", ".db").apply { writeBytes(backup.content) }
        Database("jdbc:sqlite:$file").use { restored ->
            restored.transaction {
                assertEquals(listOf("кофе"), query("SELECT note FROM operation") { it.getString(1) })
                assertEquals(listOf(60000L), query("SELECT limit_minor FROM budget") { it.getLong(1) })
                assertEquals(listOf(1, 2), query("SELECT version FROM schema_version") { it.getInt(1) })
            }
        }
    }

    @Test
    fun `backup command sends the file`() = runTest {
        app.send("кофе 5")
        app.send("такси 10")
        app.telegram.clear()
        app.handler.onMessage(finance.telegram.Message(1, finance.telegram.User(app.chatId), finance.telegram.Chat(app.chatId), "/backup"))

        val upload = app.telegram.last("sendDocument")
        assertTrue("filename=\"finance-2026-11-08.db\"" in upload.raw)
        assertTrue("SQLite format 3" in upload.raw)
        assertTrue("💾 Бэкап базы на 08.11.2026 · 2 операции" in upload.raw)
        assertTrue(app.chatId.toString() in upload.raw)
    }

    @Test
    fun `weekly backup after notify time`() = runTest {
        assertEquals(1, tickAt("2026-11-08T12:00").size, "first backup is sent right away")
        assertEquals(0, tickAt("2026-11-14T23:00").size)
        assertEquals(0, tickAt("2026-11-15T09:59").size)
        assertEquals(1, tickAt("2026-11-15T10:00").size)
        assertEquals(0, tickAt("2026-11-15T18:00").size)
        assertEquals(1, tickAt("2026-12-01T11:00").size, "after a long pause a backup is sent once")
        assertEquals(0, tickAt("2026-12-01T11:01").size)
    }
}
