package finance.service

import finance.db.Database
import finance.db.query
import java.nio.file.Files
import java.time.LocalDate
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

data class Backup(val fileName: String, val content: ByteArray, val operations: Int)

class BackupService(private val db: Database) {

    /** Makes a consistent snapshot of the whole database. */
    fun create(today: LocalDate): Backup {
        val dir = Files.createTempDirectory("finance-backup")
        val file = dir.resolve("finance-$today.db")
        try {
            db.backupTo(file)
            val operations = db.transaction { query("SELECT COUNT(*) FROM operation") { it.getInt(1) }.single() }
            return Backup(file.fileName.toString(), file.readBytes(), operations)
        } finally {
            file.deleteIfExists()
            dir.deleteIfExists()
        }
    }
}
