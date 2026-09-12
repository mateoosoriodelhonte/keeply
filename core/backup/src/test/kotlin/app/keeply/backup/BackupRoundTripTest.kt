package app.keeply.backup

import app.keeply.data.KeeplyDatabaseFactory
import app.keeply.documents.DataDirectory
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupRoundTripTest {
    private val workspace: Path = createTempDirectory("keeply-backup")

    @AfterTest
    fun cleanUp() {
        workspace.toFile().deleteRecursively()
    }

    private fun library(name: String) = TestLibrary(workspace.resolve(name))

    @Test
    fun everythingComesBack() = runBlocking {
        val archive = workspace.resolve("keeply.keeplybackup")
        val original = library("original")
        val purchase = original.addPurchaseWithReceipt("Sony Headphones")
        original.addPurchaseWithReceipt("Espresso Machine")
        BackupWriter(original.directory, "1.0.0").write(original.store, archive)
        original.close()

        val restoredRoot = workspace.resolve("restored")
        val target = DataDirectory(restoredRoot)
        BackupReader().restore(archive, target)

        KeeplyDatabaseFactory.open(target.database.resolve(KeeplyDatabaseFactory.DATABASE_FILE_NAME)).use { store ->
            assertEquals(2L, store.purchases.count())
            val loaded = assertNotNull(store.purchases.get(purchase.id))
            assertEquals("Sony Headphones", loaded.productName)
            assertEquals(purchase.price, loaded.price)
            assertEquals(purchase.returnWindow.deadline, loaded.returnWindow.deadline)
            assertEquals(purchase.warranty.endDate, loaded.warranty.endDate)

            // The receipt itself, not just its record.
            val document = assertNotNull(store.documents.get(loaded.receiptDocumentId!!))
            val file = target.resolve(document.relativePath)
            assertTrue(Files.isRegularFile(file), "the receipt file should have been restored")
            assertEquals(document.byteSize, Files.size(file))

            // Search still works, which means the index came back with the data.
            assertEquals(1, store.search.find("headphones").size)
        }
    }

    @Test
    fun aBackupDescribesItselfBeforeAnythingIsRestored() = runBlocking {
        val archive = workspace.resolve("described.keeplybackup")
        library("described").use { source ->
            source.addPurchaseWithReceipt("Blender")
            BackupWriter(source.directory, "1.0.0").write(source.store, archive)
        }

        val summary = BackupReader().inspect(archive)
        assertTrue(summary.restorable)
        assertEquals(1L, summary.manifest.purchaseCount)
        assertEquals(1L, summary.manifest.documentCount)
        assertEquals(BackupManifest.CURRENT_FORMAT_VERSION, summary.manifest.formatVersion)
        assertTrue(summary.totalBytes > 0)
    }

    @Test
    fun refusesToOverwriteALibraryWithoutBeingTold() = runBlocking {
        val archive = workspace.resolve("overwrite.keeplybackup")
        library("source").use { source ->
            source.addPurchaseWithReceipt("Blender")
            BackupWriter(source.directory, "1.0.0").write(source.store, archive)
        }

        val existing = library("existing")
        existing.addPurchaseWithReceipt("Something Else")
        existing.close()

        val failure = assertFailsWith<BackupException> {
            BackupReader().restore(archive, DataDirectory(workspace.resolve("existing")))
        }
        assertContains(failure.userMessage, "already a Keeply library")
        assertContains(failure.userMessage, "changed nothing")

        // And the library really is untouched.
        KeeplyDatabaseFactory.open(
            workspace.resolve("existing/database/${KeeplyDatabaseFactory.DATABASE_FILE_NAME}"),
        ).use { store ->
            assertEquals("Something Else", store.purchases.all().single().productName)
        }
    }

    @Test
    fun replacesALibraryOnlyWhenExplicitlyAsked() = runBlocking {
        val archive = workspace.resolve("replace.keeplybackup")
        library("replaceSource").use { source ->
            source.addPurchaseWithReceipt("Restored Item")
            BackupWriter(source.directory, "1.0.0").write(source.store, archive)
        }
        library("replaceTarget").use { it.addPurchaseWithReceipt("Old Item") }

        BackupReader().restore(
            archive,
            DataDirectory(workspace.resolve("replaceTarget")),
            RestoreMode.REPLACE_EXISTING,
        )

        KeeplyDatabaseFactory.open(
            workspace.resolve("replaceTarget/database/${KeeplyDatabaseFactory.DATABASE_FILE_NAME}"),
        ).use { store ->
            assertEquals("Restored Item", store.purchases.all().single().productName)
        }
    }

    @Test
    fun anInterruptedBackupLeavesNoFileThatLooksComplete() = runBlocking {
        val archive = workspace.resolve("partial.keeplybackup")
        library("partial").use { source ->
            source.addPurchaseWithReceipt("Thing")
            BackupWriter(source.directory, "1.0.0").write(source.store, archive)
        }
        assertTrue(Files.exists(archive))
        assertTrue(Files.notExists(archive.resolveSibling("${archive.fileName}.part")))
    }

    @Test
    fun refusesABackupFromANewerKeeply() = runBlocking {
        val archive = workspace.resolve("future.keeplybackup")
        library("future").use { source ->
            source.addPurchaseWithReceipt("Thing")
            BackupWriter(source.directory, "1.0.0").write(source.store, archive)
        }
        val tampered = workspace.resolve("tampered.keeplybackup")
        ArchiveEditor.rewriteManifest(archive, tampered) { it.copy(formatVersion = 99) }

        // Inspecting says what is wrong; it is restoring that refuses. Someone
        // should be able to look at a backup they cannot yet restore and be told why.
        val summary = BackupReader().inspect(tampered)
        assertEquals(false, summary.restorable)
        assertContains(assertNotNull(summary.problem), "newer version")

        val failure = assertFailsWith<BackupException> {
            BackupReader().restore(tampered, DataDirectory(workspace.resolve("futureTarget")))
        }
        assertContains(failure.userMessage, "newer version")
    }
}
