package finance.bot

import finance.telegram.BotCommand
import finance.telegram.TelegramApi
import finance.telegram.Update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory

/** Long-polling loop. Only the owner's updates are processed. */
class Bot(
    private val api: TelegramApi,
    private val handler: BotHandler,
    private val ownerId: Long,
) {
    private val log = LoggerFactory.getLogger(Bot::class.java)

    suspend fun run() {
        try {
            api.setMyCommands(COMMANDS)
        } catch (e: Exception) {
            log.warn("Failed to set bot commands: {}", e.message)
        }

        var offset: Long? = null
        log.info("Bot started")
        while (currentCoroutineContext().isActive) {
            val updates = try {
                api.getUpdates(offset, POLL_TIMEOUT_SECONDS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("getUpdates failed: {}", e.message)
                delay(RETRY_DELAY_MS)
                continue
            }
            for (update in updates) {
                offset = update.updateId + 1
                try {
                    dispatch(update)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Failed to handle update {}", update.updateId, e)
                    runCatching { api.sendMessage(ownerId, "⚠️ Что-то пошло не так: ${e.message}") }
                }
            }
        }
    }

    internal suspend fun dispatch(update: Update) {
        update.message?.let { message ->
            if (message.from?.id == ownerId) handler.onMessage(message) else log.info("Ignored message from user {}", message.from?.id)
        }
        update.callbackQuery?.let { callback ->
            if (callback.from.id == ownerId) handler.onCallback(callback) else api.answerCallbackQuery(callback.id)
        }
    }

    private companion object {
        const val POLL_TIMEOUT_SECONDS = 50
        const val RETRY_DELAY_MS = 5_000L

        val COMMANDS = listOf(
            BotCommand("today", "Операции за сегодня"),
            BotCommand("week", "Итог недели"),
            BotCommand("month", "Отчёт за месяц"),
            BotCommand("last", "Последние операции"),
            BotCommand("undo", "Удалить последнюю операцию"),
            BotCommand("budget", "Бюджеты на месяц"),
            BotCommand("recurring", "Регулярные платежи"),
            BotCommand("categories", "Категории"),
            BotCommand("rate", "Курсы валют"),
            BotCommand("cancel", "Отменить текущий ввод"),
            BotCommand("help", "Как пользоваться"),
        )
    }
}
