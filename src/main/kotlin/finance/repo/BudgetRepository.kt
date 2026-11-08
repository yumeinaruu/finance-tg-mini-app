package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.query
import finance.domain.Category
import java.time.YearMonth

class BudgetRepository(private val db: Database) {

    fun set(categoryId: Long, month: YearMonth, limitMinor: Long) = db.transaction {
        execute(
            "INSERT OR REPLACE INTO budget (category_id, month, limit_minor) VALUES (?, ?, ?)",
            categoryId, month.toString(), limitMinor,
        )
    }

    fun delete(categoryId: Long, month: YearMonth): Boolean = db.transaction {
        execute("DELETE FROM budget WHERE category_id = ? AND month = ?", categoryId, month.toString()) > 0
    }

    fun find(categoryId: Long, month: YearMonth): Long? = db.transaction {
        query("SELECT limit_minor FROM budget WHERE category_id = ? AND month = ?", categoryId, month.toString()) {
            it.getLong(1)
        }.firstOrNull()
    }

    /** Budgets of active categories for [month] with their limits. */
    fun forMonth(month: YearMonth): List<Pair<Category, Long>> = db.transaction {
        query(
            """
            SELECT c.id, c.name, c.emoji, c.type, b.limit_minor
            FROM budget b JOIN category c ON c.id = b.category_id
            WHERE b.month = ? AND c.archived = 0
            ORDER BY c.sort_order, c.id
            """.trimIndent(),
            month.toString(),
        ) { it.toCategory() to it.getLong("limit_minor") }
    }

    /** Copies budgets of active categories; existing budgets of [to] are kept. Returns the number copied. */
    fun copy(from: YearMonth, to: YearMonth): Int = db.transaction {
        execute(
            """
            INSERT OR IGNORE INTO budget (category_id, month, limit_minor)
            SELECT b.category_id, ?, b.limit_minor
            FROM budget b JOIN category c ON c.id = b.category_id
            WHERE b.month = ? AND c.archived = 0
            """.trimIndent(),
            to.toString(), from.toString(),
        )
    }

    fun alerted(categoryId: Long, month: YearMonth, threshold: Int): Boolean = db.transaction {
        query(
            "SELECT 1 FROM budget_alert WHERE category_id = ? AND month = ? AND threshold = ?",
            categoryId, month.toString(), threshold,
        ) { true }.isNotEmpty()
    }

    fun markAlerted(categoryId: Long, month: YearMonth, threshold: Int) = db.transaction {
        execute(
            "INSERT OR IGNORE INTO budget_alert (category_id, month, threshold) VALUES (?, ?, ?)",
            categoryId, month.toString(), threshold,
        )
    }

    fun deleteForCategory(categoryId: Long) = db.transaction {
        execute("DELETE FROM budget_alert WHERE category_id = ?", categoryId)
        execute("DELETE FROM budget WHERE category_id = ?", categoryId)
    }
}
