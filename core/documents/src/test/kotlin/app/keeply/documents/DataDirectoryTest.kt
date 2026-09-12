package app.keeply.documents

import app.keeply.domain.DocumentKind
import app.keeply.domain.Ids
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataDirectoryTest {
    private val workspace: Path = createTempDirectory("keeply-dir")
    private val directory = DataDirectory(workspace.resolve("keeply-data")).create()

    @AfterTest
    fun cleanUp() {
        workspace.toFile().deleteRecursively()
    }

    @Test
    fun createsOnePredictableLayout() {
        listOf(
            directory.database,
            directory.receipts,
            directory.manuals,
            directory.photos,
            directory.thumbnails,
            directory.processed,
        ).forEach { assertTrue(Files.isDirectory(it), "$it should exist") }
    }

    @Test
    fun separatesWhatCanBeRebuiltFromWhatCannot() {
        // The storage screen offers to clear derived data. Originals must never be
        // in that list.
        assertEquals(listOf(directory.thumbnails, directory.processed), directory.derivedDirectories)
        assertTrue(directory.receipts !in directory.derivedDirectories)
        assertTrue(directory.manuals !in directory.derivedDirectories)
    }

    @Test
    fun spreadsFilesOverSubfoldersSoListingsStayUsable() {
        val id = Ids.generate()
        val path = directory.allocate(DocumentKind.RECEIPT, id, "png")
        assertEquals(id.take(2), path.parent.fileName.toString())
        assertEquals("$id.png", path.fileName.toString())
    }

    @Test
    fun refusesToBuildAPathFromAnythingItDidNotGenerate() {
        // Filenames come from generated ids only. This is what keeps a merchant name
        // or a date out of the data folder, and keeps "../" out of a path.
        assertFailsWith<IllegalArgumentException> {
            directory.allocate(DocumentKind.RECEIPT, "../../etc/passwd", "png")
        }
        assertFailsWith<IllegalArgumentException> {
            directory.allocate(DocumentKind.RECEIPT, "best-buy-receipt", "png")
        }
    }

    @Test
    fun refusesAnAbsurdExtension() {
        val id = Ids.generate()
        assertFailsWith<IllegalArgumentException> { directory.allocate(DocumentKind.RECEIPT, id, "") }
        assertFailsWith<IllegalArgumentException> { directory.allocate(DocumentKind.RECEIPT, id, "../sh") }
        assertFailsWith<IllegalArgumentException> {
            directory.allocate(DocumentKind.RECEIPT, id, "averylongextension")
        }
    }

    @Test
    fun refusesToResolveAPathThatEscapesTheDataFolder() {
        // Relative paths are read back from the database, which a backup archive can
        // influence. Escaping the data directory has to be impossible here too.
        assertFailsWith<IllegalArgumentException> { directory.resolve("../../../etc/passwd") }
        assertFailsWith<IllegalArgumentException> { directory.resolve("receipts/../../outside.png") }
    }

    @Test
    fun resolvesOrdinaryPathsInside() {
        val resolved = directory.resolve("receipts/ab/abc.png")
        assertTrue(resolved.startsWith(directory.root))
        assertEquals("receipts/ab/abc.png", directory.relativise(resolved))
    }

    @Test
    fun putsItselfWhereTheOperatingSystemExpects() {
        val path = DataDirectory.default().root.toString()
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> assertTrue(path.contains("Library/Application Support/Keeply"))
            os.contains("win") -> assertTrue(path.contains("Keeply"))
            else -> assertTrue(path.contains("keeply"))
        }
    }
}
