package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.query
import finance.domain.Category
import finance.domain.CategoryType
import java.sql.ResultSet

class CategoryRepository(private val db: Database) {

    fun active(type: CategoryType): List<Category> = db.transaction {
        query(
            "SELECT id, name, emoji, type FROM category WHERE type = ? AND archived = 0 ORDER BY sort_order, id",
            type.name,
        ) { it.toCategory() }
    }

    fun findById(id: Long): Category? = db.transaction {
        query("SELECT id, name, emoji, type FROM category WHERE id = ?", id) { it.toCategory() }.firstOrNull()
    }

    /** Normalized alias -> category id. */
    fun aliases(): Map<String, Long> = db.transaction {
        query("SELECT alias, category_id FROM category_alias") { it.getString(1) to it.getLong(2) }.toMap()
    }

    fun saveAlias(alias: String, categoryId: Long) = db.transaction {
        execute("INSERT OR REPLACE INTO category_alias (alias, category_id) VALUES (?, ?)", alias, categoryId)
    }
}

internal fun ResultSet.toCategory(prefix: String = ""): Category = Category(
    id = getLong("${prefix}id"),
    name = getString("${prefix}name"),
    emoji = getString("${prefix}emoji"),
    type = CategoryType.valueOf(getString("${prefix}type")),
)
