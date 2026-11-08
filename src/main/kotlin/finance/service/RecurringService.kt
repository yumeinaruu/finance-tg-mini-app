package finance.service

import finance.domain.Category
import finance.domain.Currency
import finance.domain.NewOperation
import finance.domain.Operation
import finance.domain.Recurring
import finance.domain.Schedule
import finance.rates.RateUnavailableException
import finance.repo.RecurringRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurringService(
    private val repository: RecurringRepository,
    private val entries: EntryService,
) {
    private val log = LoggerFactory.getLogger(RecurringService::class.java)

    /** Creates a payment; the first one happens on the first scheduled date after [today]. */
    fun create(
        name: String,
        category: Category,
        amountMinor: Long,
        currency: Currency,
        schedule: Schedule,
        today: LocalDate,
    ): Recurring {
        val id = repository.insert(name, category.id, amountMinor, currency, schedule, schedule.nextAfter(today))
        return checkNotNull(repository.find(id))
    }

    fun list(): List<Recurring> = repository.all()

    fun find(id: Long): Recurring? = repository.find(id)

    /**
     * Updates a payment. A new schedule starts after [today]; resuming a paused payment
     * skips the dates missed during the pause.
     */
    fun update(
        id: Long,
        name: String,
        category: Category,
        amountMinor: Long,
        currency: Currency,
        schedule: Schedule,
        paused: Boolean,
        today: LocalDate,
    ): Recurring? {
        val current = repository.find(id) ?: return null
        val nextRun = when {
            schedule != current.schedule -> schedule.nextAfter(today)
            current.paused && !paused && current.nextRun.isBefore(today) -> schedule.nextAfter(today)
            else -> current.nextRun
        }
        repository.update(id, name, category.id, amountMinor, currency, schedule, nextRun, paused)
        return repository.find(id)
    }

    fun delete(id: Long): Boolean = repository.delete(id)

    /**
     * Adds operations for all due payments, including dates missed while the bot was down.
     * Today's payments are added only from [notifyAt]. A payment whose rate is unavailable is retried later.
     */
    suspend fun processDue(now: LocalDateTime, notifyAt: LocalTime): List<Operation> {
        val today = now.toLocalDate()
        val added = mutableListOf<Operation>()
        for (payment in repository.dueOn(today)) {
            var date = payment.nextRun
            while (!date.isAfter(today)) {
                if (date == today && now.toLocalTime().isBefore(notifyAt)) break
                val entry = NewOperation(payment.category.type, payment.amountMinor, payment.currency, date, payment.name)
                val operation = try {
                    entries.save(entry, payment.category, payment.id)
                } catch (e: RateUnavailableException) {
                    log.warn("Recurring payment {} postponed: {}", payment.id, e.message)
                    break
                }
                added += operation
                date = payment.schedule.nextAfter(date)
                repository.setNextRun(payment.id, date)
            }
        }
        return added
    }
}
