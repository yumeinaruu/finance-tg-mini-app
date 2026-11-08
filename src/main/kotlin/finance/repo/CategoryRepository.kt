package finance.repo

import finance.db.Database
import finance.db.execute
import finance.db.insert
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

    /** Active categories of both types: expenses first. */
    fun activeAll(): List<Category> = CategoryType.entries.flatMap { active(it) }

    /** All categories of [type] including archived ones, paired with the archived flag. */
    fun allWithArchived(type: CategoryType): List<Pair<Category, Boolean>> = db.transaction {
        query("SELECT id, name, emoji, type, archived FROM category WHERE type = ? ORDER BY id", type.name) {
            it.toCategory() to (it.getInt("archived") == 1)
        }
    }

    fun findById(id: Long): Category? = db.transaction {
        query("SELECT id, name, emoji, type FROM category WHERE id = ?", id) { it.toCategory() }.firstOrNull()
    }

    fun insert(name: String, emoji: String, type: CategoryType): Long = db.transaction {
        insert(
            "INSERT INTO category (name, emoji, type, sort_order) " +
                "VALUES (?, ?, ?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM category WHERE type = ?))",
            name, emoji, type.name, type.name,
        )
    }

    fun update(id: Long, name: String, emoji: String) = db.transaction {
        execute("UPDATE category SET name = ?, emoji = ? WHERE id = ?", name, emoji, id)
    }

    fun setArchived(id: Long, archived: Boolean) = db.transaction {
        execute("UPDATE category SET archived = ? WHERE id = ?", if (archived) 1 else 0, id)
    }

    /** Deletes the category with its aliases; the caller makes sure nothing else references it. */
    fun delete(id: Long) = db.transaction {
        execute("DELETE FROM category_alias WHERE category_id = ?", id)
        execute("DELETE FROM category WHERE id = ?", id)
    }

    /** Normalized alias -> category id. */
    fun aliases(): Map<String, Long> = db.transaction {
        query("SELECT alias, category_id FROM category_alias") { it.getString(1) to it.getLong(2) }.toMap()
    }

    fun aliasesByCategory(): Map<Long, List<String>> = db.transaction {
        query("SELECT alias, category_id FROM category_alias ORDER BY rowid") { it.getLong(2) to it.getString(1) }
            .groupBy({ it.first }, { it.second })
    }

    fun saveAlias(alias: String, categoryId: Long) = db.transaction {
        execute("INSERT OR REPLACE INTO category_alias (alias, category_id) VALUES (?, ?)", alias, categoryId)
    }

    fun deleteAlias(alias: String): Boolean = db.transaction {
        execute("DELETE FROM category_alias WHERE alias = ?", alias) > 0
    }

    fun moveAliases(fromId: Long, toId: Long) = db.transaction {
        execute("UPDATE category_alias SET category_id = ? WHERE category_id = ?", toId, fromId)
    }
}

internal fun ResultSet.toCategory(prefix: String = ""): Category = Category(
    id = getLong("${prefix}id"),
    name = getString("${prefix}name"),
    emoji = getString("${prefix}emoji"),
    type = CategoryType.valueOf(getString("${prefix}type")),
)
