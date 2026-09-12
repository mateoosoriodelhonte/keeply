package app.keeply.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.keeply.data.sql.KeeplyDatabase
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Raised when a database cannot be opened safely. Carries wording a person can act on. */
public class KeeplyDatabaseException(public val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/**
 * Opens Keeply's SQLite database, creating it or migrating it as needed.
 *
 * The important rule here is the one about newer databases. If someone opens a
 * store written by a later version of Keeply, this refuses rather than running
 * queries against a schema it does not understand. Losing a receipt is worse than
 * seeing an error, so the failure is loud and the data is left alone.
 */
public object KeeplyDatabaseFactory {
    private val log = LoggerFactory.getLogger(KeeplyDatabaseFactory::class.java)

    /** The schema version this build of Keeply understands. */
    public val schemaVersion: Long get() = KeeplyDatabase.Schema.version

    public const val DATABASE_FILE_NAME: String = "keeply.db"

    public fun open(databaseFile: Path): KeeplyStore {
        val parent = databaseFile.parent
        if (parent != null) Files.createDirectories(parent)

        val driver = try {
            JdbcSqliteDriver("jdbc:sqlite:${databaseFile.toAbsolutePath()}", connectionProperties())
        } catch (e: Exception) {
            throw KeeplyDatabaseException(
                "Keeply could not open its database at $databaseFile. " +
                    "Check that the folder exists and that you have permission to write to it.",
                e,
            )
        }

        return try {
            configure(driver)
            prepareSchema(driver, describe(databaseFile))
            KeeplyStore(KeeplyDatabase(driver), driver)
        } catch (e: KeeplyDatabaseException) {
            driver.close()
            throw e
        } catch (e: Exception) {
            driver.close()
            throw KeeplyDatabaseException(
                "Keeply could not prepare its database. The file may be damaged. " +
                    "Your receipts are stored as separate files and are not affected.",
                e,
            )
        }
    }

    /** An in-memory store, used by tests and by nothing else. */
    public fun openInMemory(): KeeplyStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, connectionProperties())
        configure(driver)
        prepareSchema(driver, "an in-memory database")
        return KeeplyStore(KeeplyDatabase(driver), driver)
    }

    /**
     * Per-connection settings.
     *
     * These have to be connection properties rather than statements run once after
     * opening. `foreign_keys` is per-connection state in SQLite and is off by
     * default, and the driver opens connections as it needs them, so a single
     * `PRAGMA foreign_keys = ON` would protect only the first one. A test exists for
     * exactly this, because the failure mode is silent: purchases would keep
     * pointing at receipts that no longer exist.
     */
    private fun connectionProperties(): Properties = Properties().apply {
        setProperty("foreign_keys", "true")
        setProperty("busy_timeout", "5000")
        setProperty("synchronous", "NORMAL")
    }

    private fun configure(driver: SqlDriver) {
        // Write-ahead logging is a property of the database file, not of a
        // connection, so it is set once and persists.
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
    }

    private fun prepareSchema(driver: SqlDriver, description: String) {
        val current = readUserVersion(driver)
        val target = KeeplyDatabase.Schema.version

        when {
            current == 0L && isEmpty(driver) -> {
                log.info("Creating a new Keeply database at schema version {}", target)
                KeeplyDatabase.Schema.create(driver).value
                writeUserVersion(driver, target)
            }

            current > target -> throw KeeplyDatabaseException(
                "This Keeply data was saved by a newer version of the app " +
                    "(data version $current, this app understands $target). " +
                    "Update Keeply to open it. Nothing has been changed.",
            )

            current < target -> {
                log.info("Migrating Keeply database from schema version {} to {}", current, target)
                KeeplyDatabase.Schema.migrate(driver, current, target).value
                writeUserVersion(driver, target)
            }

            else -> log.debug("Opened {} at schema version {}", description, current)
        }
    }

    /**
     * A file that exists but has no tables is a fresh database. A file with tables
     * and a user_version of zero was not written by Keeply, and is refused.
     */
    private fun isEmpty(driver: SqlDriver): Boolean {
        val tables = driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).value
        if (tables > 0L) {
            throw KeeplyDatabaseException(
                "That file is not a Keeply database. Choose a different folder, " +
                    "or use Import Backup if you are restoring from a Keeply backup.",
            )
        }
        return true
    }

    private fun readUserVersion(driver: SqlDriver): Long = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0,
    ).value

    private fun writeUserVersion(driver: SqlDriver, version: Long) {
        // PRAGMA does not accept bound parameters; the value is a Long from the
        // generated schema, never anything a user supplied.
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

    private fun describe(path: Path): String = path.fileName?.toString() ?: path.toString()
}
