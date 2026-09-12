package app.keeply.data

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rules about opening a database file, which matter more than they look.
 *
 * A person's receipts are irreplaceable. Refusing to open something Keeply does not
 * understand is always better than opening it and writing to it anyway.
 */
class DatabaseLifecycleTest {
    private val directory: Path = createTempDirectory("keeply-lifecycle")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun createsAFreshDatabaseAtTheCurrentSchemaVersion() {
        val file = directory.resolve(KeeplyDatabaseFactory.DATABASE_FILE_NAME)
        KeeplyDatabaseFactory.open(file).use { store ->
            assertEquals(0L, store.purchases.count())
        }
        assertTrue(Files.exists(file))
        assertEquals(KeeplyDatabaseFactory.schemaVersion, userVersionOf(file))
    }

    @Test
    fun reopeningKeepsWhatWasSaved() {
        val file = directory.resolve("reopen.db")
        val purchase = TestPurchases.purchase()
        KeeplyDatabaseFactory.open(file).use { it.purchases.save(purchase) }
        KeeplyDatabaseFactory.open(file).use { store ->
            assertEquals("Sony Headphones", assertNotNull(store.purchases.get(purchase.id)).productName)
        }
    }

    @Test
    fun refusesToOpenDataWrittenByANewerKeeply() {
        // Running an older Keeply against a newer store must not quietly downgrade or
        // corrupt it. The app says so and changes nothing.
        val file = directory.resolve("future.db")
        KeeplyDatabaseFactory.open(file).use { it.purchases.save(TestPurchases.purchase()) }
        setUserVersion(file, KeeplyDatabaseFactory.schemaVersion + 5)

        val failure = assertFailsWith<KeeplyDatabaseException> { KeeplyDatabaseFactory.open(file) }
        assertContains(failure.userMessage, "newer version")
        assertContains(failure.userMessage, "Nothing has been changed")
        assertEquals(KeeplyDatabaseFactory.schemaVersion + 5, userVersionOf(file))
    }

    @Test
    fun refusesAFileThatIsNotAKeeplyDatabase() {
        val file = directory.resolve("someone-elses.db")
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE notes (body TEXT)") }
        }

        val failure = assertFailsWith<KeeplyDatabaseException> { KeeplyDatabaseFactory.open(file) }
        assertContains(failure.userMessage, "not a Keeply database")
    }

    @Test
    fun enforcesForeignKeysWhichSqliteLeavesOffByDefault() {
        val file = directory.resolve("fk.db")
        KeeplyDatabaseFactory.open(file).use { store ->
            val failure = assertFailsWith<Exception> {
                store.purchases.attach(
                    purchaseId = app.keeply.domain.PurchaseId.new(),
                    documentId = app.keeply.domain.DocumentId.new(),
                    role = AttachmentRole.MANUAL,
                )
            }
            assertTrue(
                failure.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true),
                "expected a foreign key violation, got: ${failure.message}",
            )
        }
    }

    @Test
    fun reportsAHealthyFileAsHealthy() {
        KeeplyDatabaseFactory.openInMemory().use { store ->
            store.purchases.save(TestPurchases.purchase())
            assertEquals(null, store.integrityProblem())
        }
    }

    @Test
    fun installsTheStarterCategoriesOnceOnly() {
        KeeplyDatabaseFactory.openInMemory().use { store ->
            store.categories.installDefaultsIfEmpty()
            val first = store.categories.all()
            store.categories.installDefaultsIfEmpty()
            assertEquals(first.size, store.categories.all().size)
            assertTrue(first.any { it.name == "Electronics" })
            assertTrue(first.all { it.isBuiltIn })
        }
    }

    @Test
    fun recordsItsOwnMetadata() {
        KeeplyDatabaseFactory.openInMemory().use { store ->
            store.meta.put(MetaRepository.APP_VERSION, "1.0.0")
            assertEquals("1.0.0", store.meta.get(MetaRepository.APP_VERSION))
            assertEquals(null, store.meta.get("not-set"))
        }
    }

    private fun userVersionOf(file: Path): Long = DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }
    }

    private fun setUserVersion(file: Path, version: Long) {
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = $version") }
        }
    }
}
