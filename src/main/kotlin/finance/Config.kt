package finance

import java.nio.file.Path
import java.time.LocalTime
import java.time.ZoneId
import kotlin.io.path.createDirectories

data class Config(
    val botToken: String,
    val ownerId: Long,
    val dbPath: Path,
    val zone: ZoneId,
    val httpPort: Int,
    /** When recurring payments are added and the monthly summary is sent. */
    val notifyAt: LocalTime,
    /** Public HTTPS URL of the Mini App; the menu button is set only when it is present. */
    val miniAppUrl: String?,
) {
    val dbUrl: String get() = "jdbc:sqlite:$dbPath"

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: error("Environment variable $name is not set")

            val dbPath = Path.of(env["DB_PATH"] ?: "data/finance.db").toAbsolutePath()
            dbPath.parent?.createDirectories()
            return Config(
                botToken = required("BOT_TOKEN"),
                ownerId = required("OWNER_ID").toLongOrNull() ?: error("OWNER_ID must be a number"),
                dbPath = dbPath,
                zone = ZoneId.of(env["TIMEZONE"] ?: "Europe/Minsk"),
                httpPort = env["HTTP_PORT"]?.toIntOrNull() ?: 8080,
                notifyAt = LocalTime.parse(env["NOTIFY_TIME"] ?: "10:00"),
                miniAppUrl = env["MINI_APP_URL"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
