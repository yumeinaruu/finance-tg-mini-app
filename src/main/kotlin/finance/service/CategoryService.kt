package finance.service

import finance.domain.Category
import finance.domain.CategoryType
import finance.repo.BudgetRepository
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.RecurringRepository

/** Category name with optional emoji as typed by the user, e.g. "+Кэшбэк 💸". */
data class CategoryInput(val name: String, val emoji: String?, val type: CategoryType) {
    companion object {
        const val MAX_NAME_LENGTH = 32

        fun parse(text: String, defaultType: CategoryType = CategoryType.EXPENSE): CategoryInput? {
            var tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            var type = defaultType
            if (tokens.firstOrNull()?.startsWith("+") == true) {
                type = CategoryType.INCOME
                tokens = listOf(tokens.first().removePrefix("+")).filter { it.isNotEmpty() } + tokens.drop(1)
            }
            val emoji = tokens.lastOrNull()?.takeIf(::isEmoji) ?: tokens.firstOrNull()?.takeIf(::isEmoji)
            val name = tokens.filter { it != emoji }.joinToString(" ")
            if (name.isEmpty() && emoji == null) return null
            return CategoryInput(name.replaceFirstChar { it.uppercaseChar() }, emoji, type)
        }

        private fun isEmoji(token: String) = token.none { it.isLetterOrDigit() || it in "+-.,!?()" }
    }
}

data class CategoryUsage(val operations: Int, val totalMinor: Long, val recurring: Int) {
    val isEmpty: Boolean get() = operations == 0 && recurring == 0
}

sealed interface AddCategoryResult {
    val category: Category

    data class Created(override val category: Category) : AddCategoryResult
    data class Restored(override val category: Category) : AddCategoryResult
    data class Exists(override val category: Category) : AddCategoryResult
}

class CategoryService(
    private val categories: CategoryRepository,
    private val operations: OperationRepository,
    private val budgets: BudgetRepository,
    private val recurring: RecurringRepository,
) {
    fun active(type: CategoryType): List<Category> = categories.active(type)

    /** Active categories with their aliases. */
    fun overview(): List<Pair<Category, List<String>>> {
        val aliases = categories.aliasesByCategory()
        return categories.activeAll().map { it to aliases[it.id].orEmpty() }
    }

    fun match(note: String?, type: CategoryType): Category? =
        CategoryMatcher.match(note, categories.active(type), categories.aliases())

    /** Finds an active category by exact name, alias or unique name prefix. */
    fun find(query: String, type: CategoryType? = null): Category? {
        val key = key(query)
        if (key.isEmpty()) return null
        val pool = categories.activeAll().filter { type == null || it.type == type }
        pool.firstOrNull { key(it.name) == key }?.let { return it }
        categories.aliases()[key]?.let { id -> pool.firstOrNull { it.id == id } }?.let { return it }
        return pool.filter { key(it.name).startsWith(key) }.singleOrNull()
    }

    fun add(input: CategoryInput): AddCategoryResult {
        val existing = categories.allWithArchived(input.type).firstOrNull { (category, _) -> key(category.name) == key(input.name) }
        if (existing != null) {
            val (category, archived) = existing
            if (!archived) return AddCategoryResult.Exists(category)
            categories.setArchived(category.id, false)
            input.emoji?.let { categories.update(category.id, category.name, it) }
            return AddCategoryResult.Restored(categories.findById(category.id)!!)
        }
        val id = categories.insert(input.name, input.emoji ?: DEFAULT_EMOJI, input.type)
        return AddCategoryResult.Created(categories.findById(id)!!)
    }

    /** Returns the updated category, or null when another category already has the new name. */
    fun rename(category: Category, newName: String?, newEmoji: String?): Category? {
        val name = newName?.takeIf { it.isNotBlank() } ?: category.name
        val clash = categories.allWithArchived(category.type)
            .any { (other, _) -> other.id != category.id && key(other.name) == key(name) }
        if (clash) return null
        categories.update(category.id, name, newEmoji ?: category.emoji)
        return categories.findById(category.id)
    }

    fun usage(categoryId: Long): CategoryUsage {
        val (count, total) = operations.usage(categoryId)
        return CategoryUsage(count, total, recurring.countByCategory(categoryId))
    }

    /** Deletes a category that has no operations or recurring payments. */
    fun delete(categoryId: Long): Boolean {
        if (!usage(categoryId).isEmpty) return false
        budgets.deleteForCategory(categoryId)
        categories.delete(categoryId)
        return true
    }

    fun archive(categoryId: Long) = categories.setArchived(categoryId, true)

    /** Moves operations, recurring payments and aliases to [targetId], then deletes the category. */
    fun moveAndDelete(categoryId: Long, targetId: Long) {
        operations.moveCategory(categoryId, targetId)
        recurring.moveCategory(categoryId, targetId)
        categories.moveAliases(categoryId, targetId)
        budgets.deleteForCategory(categoryId)
        categories.delete(categoryId)
    }

    fun findById(id: Long): Category? = categories.findById(id)

    /** Binds a word to a category; returns the normalized word or null when it has no letters. */
    fun setAlias(word: String, category: Category): String? {
        val alias = CategoryMatcher.normalize(word).takeIf { it.isNotEmpty() } ?: return null
        categories.saveAlias(alias, category.id)
        return alias
    }

    fun removeAlias(word: String): Boolean = categories.deleteAlias(CategoryMatcher.normalize(word))

    private fun key(name: String) = CategoryMatcher.words(name).joinToString(" ")

    companion object {
        const val DEFAULT_EMOJI = "📁"
    }
}
