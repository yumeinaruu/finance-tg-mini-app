package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.insert
import finance.db.query
import finance.domain.Currency
import finance.domain.NewOperation
import finance.domain.Operation
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

class OperationRepository(private val db: Database) {

    fun insert(entry: NewOperation, categoryId: Long, rate: BigDecimal, amountBaseMinor: Long): Long = db.transaction {
        insert(
            """
            INSERT INTO operation (category_id, amount_minor, currency, rate, amount_base_minor, occurred_on, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            categoryId, entry.amountMinor, entry.currency.code, rate.toPlainString(), amountBaseMinor,
            entry.date.toString(), entry.note, Instant.now().toString(),
        )
    }

    fun find(id: Long): Operation? = db.transaction {
        query("$SELECT WHERE o.id = ?", id) { it.toOperation() }.firstOrNull()
    }

    /** Most recently entered operations first. */
    fun last(limit: Int): List<Operation> = db.transaction {
        query("$SELECT ORDER BY o.id DESC LIMIT ?", limit) { it.toOperation() }
    }

    fun between(from: LocalDate, to: LocalDate): List<Operation> = db.transaction {
        query(
            "$SELECT WHERE o.occurred_on BETWEEN ? AND ? ORDER BY o.occurred_on, o.id",
            from.toString(), to.toString(),
        ) { it.toOperation() }
    }

    fun updateCategory(id: Long, categoryId: Long): Boolean = db.transaction {
        execute("UPDATE operation SET category_id = ? WHERE id = ?", categoryId, id) > 0
    }

    fun delete(id: Long): Boolean = db.transaction {
        execute("DELETE FROM operation WHERE id = ?", id) > 0
    }

    private fun ResultSet.toOperation() = Operation(
        id = getLong("id"),
        category = toCategory(prefix = "c_"),
        amountMinor = getLong("amount_minor"),
        currency = Currency.valueOf(getString("currency")),
        rate = BigDecimal(getString("rate")),
        amountBaseMinor = getLong("amount_base_minor"),
        date = LocalDate.parse(getString("occurred_on")),
        note = getString("note"),
    )

    private companion object {
        const val SELECT = """
            SELECT o.id, o.amount_minor, o.currency, o.rate, o.amount_base_minor, o.occurred_on, o.note,
                   c.id AS c_id, c.name AS c_name, c.emoji AS c_emoji, c.type AS c_type
            FROM operation o JOIN category c ON c.id = o.category_id
        """
    }
}
