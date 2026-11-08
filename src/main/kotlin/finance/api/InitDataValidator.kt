package finance.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Validates Mini App launch data: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 * Only the bot owner is let in.
 */
class InitDataValidator(
    botToken: String,
    private val ownerId: Long,
    private val maxAge: Duration = Duration.ofDays(1),
    private val now: () -> Instant = Instant::now,
) {
    private val secret = hmac("WebAppData".toByteArray(), botToken.toByteArray())

    fun isValid(initData: String): Boolean {
        val params = parse(initData) ?: return false
        val hash = params["hash"] ?: return false
        val dataCheckString = params.filterKeys { it != "hash" }.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val expected = hex(hmac(secret, dataCheckString.toByteArray()))
        if (!MessageDigest.isEqual(expected.toByteArray(), hash.lowercase().toByteArray())) return false

        val authDate = params["auth_date"]?.toLongOrNull() ?: return false
        if (Instant.ofEpochSecond(authDate).plus(maxAge).isBefore(now())) return false

        val userId = params["user"]?.let { runCatching { Json.parseToJsonElement(it).jsonObject["id"]?.jsonPrimitive?.longOrNull }.getOrNull() }
        return userId == ownerId
    }

    private fun parse(initData: String): Map<String, String>? = runCatching {
        initData.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            key to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
        }
    }.getOrNull()

    companion object {
        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
