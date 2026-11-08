package finance

import java.nio.file.Files
import java.time.ZoneId
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigTest {
    private val dir = Files.createTempDirectory("finance-config")

    @Test
    fun `reads required values and defaults`() {
        val dbPath = dir.resolve("nested/finance.db")
        val config = Config.fromEnv(mapOf("BOT_TOKEN" to "t", "OWNER_ID" to "42", "DB_PATH" to dbPath.toString()))

        assertEquals("t", config.botToken)
        assertEquals(42, config.ownerId)
        assertEquals(ZoneId.of("Europe/Minsk"), config.zone)
        assertEquals(8080, config.httpPort)
        assertEquals("jdbc:sqlite:$dbPath", config.dbUrl)
        assertTrue(dbPath.parent.exists(), "database directory is created")
    }

    @Test
    fun `fails on missing or invalid values`() {
        val db = dir.resolve("finance.db").toString()
        assertFailsWith<IllegalStateException> { Config.fromEnv(mapOf("OWNER_ID" to "42", "DB_PATH" to db)) }
        assertFailsWith<IllegalStateException> { Config.fromEnv(mapOf("BOT_TOKEN" to " ", "OWNER_ID" to "42", "DB_PATH" to db)) }
        assertFailsWith<IllegalStateException> { Config.fromEnv(mapOf("BOT_TOKEN" to "t", "OWNER_ID" to "abc", "DB_PATH" to db)) }
    }
}
