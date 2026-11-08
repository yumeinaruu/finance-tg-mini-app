package finance.db

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Single SQLite connection guarded by a lock: the app has one user, so this is enough
 * and keeps memory usage minimal.
 */
class Database(url: String) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(url)
    private val lock = ReentrantLock()

    init {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA busy_timeout = 5000")
        }
    }

    /** Runs [block] in a transaction; nested calls join the outer transaction. */
    fun <T> transaction(block: Connection.() -> T): T = lock.withLock {
        if (!connection.autoCommit) return@withLock connection.block()
        connection.autoCommit = false
        try {
            connection.block().also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() = connection.close()
}
