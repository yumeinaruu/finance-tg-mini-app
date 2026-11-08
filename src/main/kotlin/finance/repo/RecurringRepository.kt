package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.insert
import finance.db.query
import finance.domain.Currency
import finance.domain.Recurring
import finance.domain.Schedule
import java.sql.ResultSet
import java.time.LocalDate

class RecurringRepository(private val db: Database) {

    fun insert(name: String, categoryId: Long, amountMinor: Long, currency: Currency, schedule: Schedule, nextRun: LocalDate): Long =
        db.transaction {
            insert(
                "INSERT INTO recurring (name, category_id, amount_minor, currency, schedule, next_run) VALUES (?, ?, ?, ?, ?, ?)",
                name, categoryId, amountMinor, currency.code, schedule.encode(), nextRun.toString(),
            )
        }

    fun all(): List<Recurring> = db.transaction {
        query("$SELECT ORDER BY r.next_run, r.id") { it.toRecurring() }
    }

    fun find(id: Long): Recurring? = db.transaction {
        query("$SELECT WHERE r.id = ?", id) { it.toRecurring() }.firstOrNull()
    }

    fun dueOn(date: LocalDate): List<Recurring> = db.transaction {
        query("$SELECT WHERE r.next_run <= ? ORDER BY r.next_run, r.id", date.toString()) { it.toRecurring() }
    }

    fun setNextRun(id: Long, date: LocalDate) = db.transaction {
        execute("UPDATE recurring SET next_run = ? WHERE id = ?", date.toString(), id)
    }

    fun delete(id: Long): Boolean = db.transaction {
        execute("UPDATE operation SET recurring_id = NULL WHERE recurring_id = ?", id)
        execute("DELETE FROM recurring WHERE id = ?", id) > 0
    }

    fun countByCategory(categoryId: Long): Int = db.transaction {
        query("SELECT COUNT(*) FROM recurring WHERE category_id = ?", categoryId) { it.getInt(1) }.single()
    }

    fun moveCategory(fromId: Long, toId: Long) = db.transaction {
        execute("UPDATE recurring SET category_id = ? WHERE category_id = ?", toId, fromId)
    }

    private fun ResultSet.toRecurring() = Recurring(
        id = getLong("id"),
        name = getString("name"),
        category = toCategory(prefix = "c_"),
        amountMinor = getLong("amount_minor"),
        currency = Currency.valueOf(getString("currency")),
        schedule = Schedule.decode(getString("schedule")),
        nextRun = LocalDate.parse(getString("next_run")),
    )

    private companion object {
        const val SELECT = """
            SELECT r.id, r.name, r.amount_minor, r.currency, r.schedule, r.next_run,
                   c.id AS c_id, c.name AS c_name, c.emoji AS c_emoji, c.type AS c_type
            FROM recurring r JOIN category c ON c.id = r.category_id
        """
    }
}
