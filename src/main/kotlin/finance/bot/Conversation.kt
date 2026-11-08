package finance.bot

import finance.domain.CategoryType

/** What the bot expects as the next plain-text message. */
sealed interface Awaiting {
    /** A name for a new category; when [pendingId] is set, the pending operation goes there. */
    data class CategoryName(val type: CategoryType, val pendingId: Long?) : Awaiting

    data class Amount(val operationId: Long) : Awaiting
}

/** Conversation state of the single user; it is kept in memory and lost on restart. */
class Conversation {
    var awaiting: Awaiting? = null
}

/** A pressed inline button. [notice] is shown to the user as a toast. */
class CallbackContext(
    val chatId: Long,
    val messageId: Long,
    val ids: List<Long>,
) {
    var notice: String? = null
    val first: Long get() = ids[0]
    val second: Long? get() = ids.getOrNull(1)
}
