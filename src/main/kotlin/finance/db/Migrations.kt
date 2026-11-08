package finance.db

import org.slf4j.LoggerFactory
import java.time.Instant

/** Minimal migration runner: applies scripts from resources/db/migration in order. */
object Migrations {
    private val log = LoggerFactory.getLogger(Migrations::class.java)

    private val scripts = listOf(
        1 to "V1__init.sql",
        2 to "V2__budgets_recurring.sql",
    )

    fun apply(db: Database) = db.transaction {
        createStatement().use {
            it.execute(
                "CREATE TABLE IF NOT EXISTS schema_version " +
                    "(version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)",
            )
        }
        val applied = query("SELECT version FROM schema_version") { it.getInt(1) }.toSet()
        scripts.filter { (version, _) -> version !in applied }.forEach { (version, name) ->
            splitStatements(load(name)).forEach { sql -> createStatement().use { it.execute(sql) } }
            execute(
                "INSERT INTO schema_version (version, name, applied_at) VALUES (?, ?, ?)",
                version, name, Instant.now().toString(),
            )
            log.info("Applied migration {}", name)
        }
    }

    private fun load(name: String): String =
        Migrations::class.java.getResource("/db/migration/$name")?.readText()
            ?: error("Migration $name not found")

    internal fun splitStatements(sql: String): List<String> =
        sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
