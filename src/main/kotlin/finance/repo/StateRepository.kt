package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.query

/** Small key-value store for app bookkeeping. */
class StateRepository(private val db: Database) {
    fun get(key: String): String? = db.transaction {
        query("SELECT value FROM app_state WHERE key = ?", key) { it.getString(1) }.firstOrNull()
    }

    fun set(key: String, value: String) = db.transaction {
        execute("INSERT OR REPLACE INTO app_state (key, value) VALUES (?, ?)", key, value)
    }
}
