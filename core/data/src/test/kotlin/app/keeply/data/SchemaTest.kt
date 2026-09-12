package app.keeply.data

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the shape of the database itself.
 *
 * The delete rules in particular are load-bearing and invisible: changing one
 * `ON DELETE SET NULL` to `ON DELETE CASCADE` would turn "I deleted an old scan"
 * into "I deleted the record of what I bought", and nothing else in the test suite
 * would notice.
 */
class SchemaTest {
    private val directory: Path = createTempDirectory("keeply-schema")
    private val file: Path = directory.resolve("schema.db")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun <T> inspect(block: (Connection) -> T): T {
        KeeplyDatabaseFactory.open(file).use { it.categories.installDefaultsIfEmpty() }
        return DriverManager.getConnection("jdbc:sqlite:$file").use(block)
    }

    @Test
    fun hasEveryTableTheApplicationNeeds() {
        val tables = inspect { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                ).use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toSet()
                }
            }
        }
        val required = setOf(
            "meta", "merchant", "category", "document", "document_text",
            "purchase", "purchase_tag", "purchase_document", "line_item", "purchase_fts",
        )
        assertTrue(required.all { it in tables }, "missing tables: ${required - tables}")
    }

    @Test
    fun deletingAReceiptOrACategoryNeverDeletesAPurchase() {
        val rules = inspect { connection -> foreignKeys(connection, "purchase") }
        val byColumn = rules.associateBy { it.from }

        assertEquals("SET NULL", byColumn.getValue("receipt_document_id").onDelete)
        assertEquals("SET NULL", byColumn.getValue("product_photo_id").onDelete)
        assertEquals("SET NULL", byColumn.getValue("category_id").onDelete)
        assertEquals("SET NULL", byColumn.getValue("merchant_id").onDelete)
    }

    @Test
    fun deletingAPurchaseTakesItsOwnRowsWithIt() {
        // Tags, line items and attachment links belong to the purchase and are
        // meaningless without it.
        inspect { connection ->
            listOf("purchase_tag", "purchase_document", "line_item").forEach { table ->
                val rule = foreignKeys(connection, table).first { it.table == "purchase" }
                assertEquals("CASCADE", rule.onDelete, "$table should be removed with its purchase")
            }
            val text = foreignKeys(connection, "document_text").single()
            assertEquals("CASCADE", text.onDelete, "derived text should be removed with its document")
        }
    }

    @Test
    fun indexesTheColumnsTheHomeScreenAndLibrarySortBy() {
        val indexes = inspect { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toSet()
                }
            }
        }
        val required = setOf(
            "purchase_return_deadline",
            "purchase_warranty_end",
            "purchase_purchase_date",
            "document_sha256",
            "merchant_match_key",
        )
        assertTrue(required.all { it in indexes }, "missing indexes: ${required - indexes}")
    }

    @Test
    fun recordsTheSchemaVersionInTheFile() {
        val version = inspect { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
        }
        assertEquals(KeeplyDatabaseFactory.schemaVersion, version)
        assertTrue(version >= 1L)
    }

    private data class ForeignKeyRule(val table: String, val from: String, val onDelete: String)

    private fun foreignKeys(connection: Connection, table: String): List<ForeignKeyRule> = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA foreign_key_list($table)").use { rows ->
            generateSequence {
                if (rows.next()) {
                    ForeignKeyRule(
                        table = rows.getString("table"),
                        from = rows.getString("from"),
                        onDelete = rows.getString("on_delete"),
                    )
                } else {
                    null
                }
            }.toList()
        }
    }
}
