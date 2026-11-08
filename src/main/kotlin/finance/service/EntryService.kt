package finance.service

import finance.domain.Category
import finance.domain.CategoryType
import finance.domain.Money
import finance.domain.NewOperation
import finance.domain.Operation
import finance.rates.RateService
import finance.repo.CategoryRepository
import finance.repo.OperationRepository
import finance.repo.PendingRepository

sealed interface RecordResult {
    data class Saved(val operation: Operation) : RecordResult
    data class NeedCategory(val pendingId: Long, val entry: NewOperation, val options: List<Category>) : RecordResult
}

class EntryService(
    private val categories: CategoryRepository,
    private val operations: OperationRepository,
    private val pending: PendingRepository,
    private val rates: RateService,
) {
    suspend fun record(entry: NewOperation): RecordResult {
        val options = categories.active(entry.type)
        val category = CategoryMatcher.match(entry.note, options, categories.aliases())
            ?: return RecordResult.NeedCategory(pending.insert(entry), entry, options)
        return RecordResult.Saved(save(entry, category))
    }

    /** Saves a pending operation into the chosen category and remembers the note word as an alias. */
    suspend fun completePending(pendingId: Long, categoryId: Long): Operation? {
        val entry = pending.find(pendingId) ?: return null
        val category = categories.findById(categoryId) ?: return null
        val operation = save(entry, category)
        pending.delete(pendingId)
        learnAlias(entry.note, category)
        return operation
    }

    fun cancelPending(pendingId: Long): Boolean = pending.delete(pendingId)

    fun recategorize(operationId: Long, categoryId: Long): Operation? {
        val operation = operations.find(operationId) ?: return null
        val category = categories.findById(categoryId) ?: return null
        operations.updateCategory(operationId, category.id)
        learnAlias(operation.note, category)
        return operations.find(operationId)
    }

    fun find(operationId: Long): Operation? = operations.find(operationId)

    fun last(limit: Int): List<Operation> = operations.last(limit)

    fun delete(operationId: Long): Operation? =
        operations.find(operationId)?.also { operations.delete(it.id) }

    fun undo(): Operation? = operations.last(1).firstOrNull()?.also { operations.delete(it.id) }

    fun categoriesFor(type: CategoryType): List<Category> = categories.active(type)

    fun pending(pendingId: Long): NewOperation? = pending.find(pendingId)

    /** Changes the amount keeping the original rate. */
    fun updateAmount(operationId: Long, amountMinor: Long): Operation? {
        val operation = operations.find(operationId) ?: return null
        operations.updateAmount(operationId, amountMinor, Money.toBase(amountMinor, operation.rate))
        return operations.find(operationId)
    }

    /** Replaces all fields; the rate is re-fetched only when the currency or the date changes. */
    suspend fun update(operationId: Long, category: Category, entry: NewOperation): Operation? {
        val current = operations.find(operationId) ?: return null
        val rate = if (entry.currency == current.currency && entry.date == current.date) current.rate else rates.rate(entry.currency, entry.date)
        operations.update(operationId, category.id, entry, rate, Money.toBase(entry.amountMinor, rate))
        return operations.find(operationId)
    }

    suspend fun save(entry: NewOperation, category: Category, recurringId: Long? = null): Operation {
        val rate = rates.rate(entry.currency, entry.date)
        val id = operations.insert(entry, category.id, rate, Money.toBase(entry.amountMinor, rate), recurringId)
        return checkNotNull(operations.find(id)) { "Operation $id was not saved" }
    }

    private fun learnAlias(note: String?, category: Category) {
        val word = CategoryMatcher.words(note).firstOrNull { it.length >= 2 } ?: return
        categories.saveAlias(word, category.id)
    }
}
