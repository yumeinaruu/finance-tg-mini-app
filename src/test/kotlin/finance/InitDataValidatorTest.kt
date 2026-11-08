package finance

import finance.api.InitDataValidator
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Builds launch data signed the way Telegram does it. */
fun signedInitData(
    botToken: String,
    userId: Long,
    authDate: Instant = Instant.parse("2026-11-08T12:00:00Z"),
    extra: Map<String, String> = emptyMap(),
): String {
    val fields = mapOf(
        "auth_date" to authDate.epochSecond.toString(),
        "query_id" to "AAH",
        "user" to """{"id":$userId,"first_name":"Стас","username":"stas"}""",
    ) + extra
    val secret = InitDataValidator.hmac("WebAppData".toByteArray(), botToken.toByteArray())
    val check = fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
    val hash = InitDataValidator.hex(InitDataValidator.hmac(secret, check.toByteArray()))
    return (fields + ("hash" to hash)).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }
}

class InitDataValidatorTest {
    private val token = "123:secret"
    private val now = Instant.parse("2026-11-08T13:00:00Z")
    private val validator = InitDataValidator(token, ownerId = 42, maxAge = Duration.ofDays(1)) { now }

    @Test
    fun `accepts data signed for the owner`() {
        assertTrue(validator.isValid(signedInitData(token, 42)))
        assertTrue(validator.isValid(signedInitData(token, 42, extra = mapOf("signature" to "abc", "chat_type" to "sender"))))
    }

    @Test
    fun `rejects forged, foreign, stale and malformed data`() {
        assertFalse(validator.isValid(signedInitData("other:token", 42)), "wrong bot token")
        assertFalse(validator.isValid(signedInitData(token, 7)), "another user")
        assertFalse(validator.isValid(signedInitData(token, 42, authDate = now.minus(Duration.ofDays(2)))), "too old")
        assertFalse(validator.isValid(signedInitData(token, 42).replace("query_id=AAH", "query_id=AAX")), "tampered field")
        assertFalse(validator.isValid(signedInitData(token, 42).substringBefore("&hash=")), "no hash")
        assertFalse(validator.isValid(""))
        assertFalse(validator.isValid("%%%"))
    }
}
