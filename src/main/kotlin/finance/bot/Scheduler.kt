package finance.bot

import finance.service.BudgetService
import finance.service.RecurringService
import finance.service.ReportService
import finance.repo.StateRepository
import finance.telegram.TelegramApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Adds due recurring payments and sends the monthly summary. */
class Scheduler(
    private val api: TelegramApi,
    private val ownerId: Long,
    private val recurring: RecurringService,
    private val budgets: BudgetService,
    private val reports: ReportService,
    private val state: StateRepository,
    private val notifyAt: LocalTime,
    private val now: () -> LocalDateTime,
) {
    private val log = LoggerFactory.getLogger(Scheduler::class.java)

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Scheduler tick failed", e)
            }
            delay(TICK_MS)
        }
    }

    suspend fun tick() {
        addRecurring()
        sendMonthlySummary()
    }

    private suspend fun addRecurring() {
        val now = now()
        recurring.processDue(now, notifyAt).forEach { op ->
            val text = Texts.withAlert(Texts.recurringAdded(op, now.toLocalDate()), budgets.alertFor(op, now.toLocalDate()))
            api.sendMessage(ownerId, text, Keyboards.recurringOperation(op))
        }
    }

    /** On the first tick after [notifyAt] in a new month, sends last month's report. */
    private suspend fun sendMonthlySummary() {
        val now = now()
        val month = YearMonth.from(now)
        val last = state.get(SUMMARY_KEY)?.let(YearMonth::parse)
        if (last == null) {
            state.set(SUMMARY_KEY, month.toString())
            return
        }
        if (!last.isBefore(month) || now.toLocalTime().isBefore(notifyAt)) return

        val previous = month.minusMonths(1)
        val previousBudgets = budgets.progress(previous)
        val report = Texts.monthReport(previous, reports.summary(previous.atDay(1), previous.atEndOfMonth()), previousBudgets)
        val offerCopy = previousBudgets.isNotEmpty() && budgets.progress(month).isEmpty()
        if (offerCopy) {
            api.sendMessage(ownerId, "$report\n\nСкопировать бюджеты на ${Texts.monthName(month)}?", Keyboards.copyBudgets(previous))
        } else {
            api.sendMessage(ownerId, report)
        }
        state.set(SUMMARY_KEY, month.toString())
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val SUMMARY_KEY = "monthly_summary_sent"
    }
}
