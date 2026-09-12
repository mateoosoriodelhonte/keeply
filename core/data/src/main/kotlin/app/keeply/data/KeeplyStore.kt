package app.keeply.data

import app.cash.sqldelight.db.SqlDriver
import app.keeply.data.sql.KeeplyDatabase

/**
 * Everything Keeply has saved, behind one handle.
 *
 * Repositories are exposed rather than the generated queries, so callers work in
 * terms of purchases and receipts instead of rows, and so the one place that knows
 * how a purchase is spread across five tables stays in one place.
 */
public class KeeplyStore internal constructor(internal val database: KeeplyDatabase, private val driver: SqlDriver) : AutoCloseable {

    public val purchases: PurchaseRepository = PurchaseRepository(database)
    public val merchants: MerchantRepository = MerchantRepository(database)
    public val categories: CategoryRepository = CategoryRepository(database)
    public val documents: DocumentRepository = DocumentRepository(database)
    public val search: SearchRepository = SearchRepository(database)
    public val meta: MetaRepository = MetaRepository(database)

    /** Runs [block] in a transaction, so a half-saved purchase can never be observed. */
    public fun <T> transaction(block: () -> T): T {
        var result: T? = null
        database.transaction { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Reclaims space after a lot of deletion. Safe to call, never automatic. */
    public fun compact() {
        driver.execute(null, "VACUUM", 0)
    }

    /** Raises an error if SQLite reports the file is damaged. Used by the storage screen. */
    public fun integrityProblem(): String? {
        val result = StringBuilder()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA integrity_check",
            mapper = { cursor ->
                while (cursor.next().value) {
                    cursor.getString(0)?.let { result.appendLine(it) }
                }
                app.cash.sqldelight.db.QueryResult.Unit
            },
            parameters = 0,
        )
        val report = result.toString().trim()
        return if (report == "ok") null else report
    }

    override fun close() {
        driver.close()
    }
}

/** Keeply's own notes about the store, written into backups so an import can check them. */
public class MetaRepository internal constructor(private val database: KeeplyDatabase) {
    public fun get(key: String): String? = database.metaQueries.get(key).executeAsOneOrNull()

    public fun put(key: String, value: String) {
        database.metaQueries.put(key, value)
    }

    public fun all(): Map<String, String> = database.metaQueries.all().executeAsList().associate { it.key to it.value_ }

    public companion object {
        public const val CREATED_AT: String = "created_at"
        public const val APP_VERSION: String = "app_version"
        public const val DATA_DIRECTORY_VERSION: String = "data_directory_version"
    }
}
