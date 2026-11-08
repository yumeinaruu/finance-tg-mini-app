package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.insert
import finance.db.query
import finance.domain.CategoryType
import finance.domain.Currency
import finance.domain.NewOperation
import java.time.Instant
import java.time.LocalDate

/** Operations waiting for the user to choose a category. */
class PendingRepository(private val db: Database) {

    fun insert(entry: NewOperation): Long = db.transaction {
        insert(
            "INSERT INTO pending_operation (type, amount_minor, currency, occurred_on, note, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            entry.type.name, entry.amountMinor, entry.currency.code, entry.date.toString(), entry.note,
            Instant.now().toString(),
        )
    }

    fun find(id: Long): NewOperation? = db.transaction {
        query("SELECT type, amount_minor, currency, occurred_on, note FROM pending_operation WHERE id = ?", id) {
            NewOperation(
                type = CategoryType.valueOf(it.getString("type")),
                amountMinor = it.getLong("amount_minor"),
                currency = Currency.valueOf(it.getString("currency")),
                date = LocalDate.parse(it.getString("occurred_on")),
                note = it.getString("note"),
            )
        }.firstOrNull()
    }

    fun delete(id: Long): Boolean = db.transaction {
        execute("DELETE FROM pending_operation WHERE id = ?", id) > 0
    }
}
