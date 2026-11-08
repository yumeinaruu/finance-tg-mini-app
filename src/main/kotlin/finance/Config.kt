package finance

import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.createDirectories

data class Config(
    val botToken: String,
    val ownerId: Long,
    val dbPath: Path,
    val zone: ZoneId,
    val httpPort: Int,
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
            )
        }
    }
}
