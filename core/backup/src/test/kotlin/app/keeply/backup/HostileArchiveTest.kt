package app.keeply.backup

import app.keeply.documents.DataDirectory
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A backup file can come from anywhere: a download, a shared drive, someone
 * else's machine. Each test here is an archive built the way an attacker would
 * build it, and each asserts that Keeply refuses it and writes nothing.
 */
class HostileArchiveTest {
    private val workspace: Path = createTempDirectory("keeply-hostile")
    private val target: Path = workspace.resolve("data")
    private val reader = BackupReader()

    @AfterTest
    fun cleanUp() {
        workspace.toFile().deleteRecursively()
    }

    private fun archive(name: String, vararg entries: Pair<String, ByteArray>): Path =
        workspace.resolve(name).also { ArchiveEditor.of(it, entries.toList()) }

    private fun restoring(archive: Path): BackupException = assertFailsWith {
        runBlocking { reader.restore(archive, DataDirectory(target)) }
    }

    private fun assertWroteNothing() {
        assertTrue(
            Files.notExists(target) || Files.walk(target).use { it.noneMatch(Files::isRegularFile) },
            "a refused archive must leave nothing behind",
        )
        assertTrue(
            Files.notExists(workspace.resolve("data.restoring")),
            "the staging folder should have been cleaned up",
        )
    }

    @Test
    fun refusesAnEntryThatClimbsOutOfTheFolder() {
        // The classic. Written naively, this lands in the parent directory.
        val hostile = archive(
            "traversal.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "files/../../../../tmp/keeply-owned" to ArchiveEditor.bytes("pwned"),
        )
        assertContains(restoring(hostile).userMessage, "outside the folder you chose")
        assertWroteNothing()
        assertTrue(Files.notExists(workspace.parent.resolve("tmp/keeply-owned")))
    }

    @Test
    fun refusesAnAbsolutePath() {
        val hostile = archive(
            "absolute.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "/etc/keeply-owned" to ArchiveEditor.bytes("pwned"),
        )
        assertContains(restoring(hostile).userMessage, "absolute file path")
        assertWroteNothing()
    }

    @Test
    fun refusesAWindowsDriveLetter() {
        val hostile = archive(
            "drive.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "C:/Windows/System32/keeply" to ArchiveEditor.bytes("pwned"),
        )
        assertContains(restoring(hostile).userMessage, "absolute file path")
        assertWroteNothing()
    }

    @Test
    fun refusesBackslashPathsThatSomeReadersNormalise() {
        val hostile = archive(
            "backslash.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            """files\..\..\keeply-owned""" to ArchiveEditor.bytes("pwned"),
        )
        assertContains(restoring(hostile).userMessage, "does not recognise as safe")
        assertWroteNothing()
    }

    @Test
    fun refusesTheSameFileListedTwice() {
        // The second entry overwrites the first, after the first has been checked.
        // Built by hand: Java's own ZIP writer refuses to produce this, which is
        // exactly why an attacker would not use it either.
        val hostile = workspace.resolve("duplicate.zip")
        ArchiveEditor.rawZip(
            hostile,
            listOf(
                BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
                "files/receipts/ab/one.png" to ArchiveEditor.bytes("first"),
                "files/receipts/ab/one.png" to ArchiveEditor.bytes("second"),
            ),
        )
        // Refused, though not by Keeply's own check: the JDK's ZIP reader rejects a
        // central directory with duplicate names before Keeply sees the entries.
        // Keeply keeps its own check anyway, since that behaviour is a JDK hardening
        // measure rather than something guaranteed by the format.
        val message = restoring(hostile).userMessage
        assertTrue(
            message.contains("same file twice") || message.contains("damaged"),
            "expected a refusal, got: $message",
        )
        assertWroteNothing()
    }

    @Test
    fun refusesAFileKeeplyWouldNeverHavePutThere() {
        val hostile = archive(
            "stranger.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "launchd/com.example.plist" to ArchiveEditor.bytes("pwned"),
        )
        assertContains(restoring(hostile).userMessage, "did not put there")
        assertWroteNothing()
    }

    @Test
    fun refusesAnArchiveThatExpandsFarMoreThanAnyBackup() {
        // A zip bomb: a few kilobytes that become tens of megabytes of zeroes.
        val hostile = archive(
            "bomb.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "files/receipts/ab/huge.png" to ArchiveEditor.compressibleBytes(64 * 1024 * 1024),
        )
        assertContains(restoring(hostile).userMessage, "expands far more")
        assertWroteNothing()
    }

    @Test
    fun refusesAnArchiveWithNoManifest() {
        val hostile = archive("nomanifest.zip", "files/receipts/ab/one.png" to ArchiveEditor.bytes("x"))
        assertContains(restoring(hostile).userMessage, "no manifest")
        assertWroteNothing()
    }

    @Test
    fun refusesAManifestThatIsNotJson() {
        val hostile = archive(
            "badmanifest.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.bytes("this is not json"),
        )
        assertContains(restoring(hostile).userMessage, "could not be read")
        assertWroteNothing()
    }

    @Test
    fun refusesSomethingThatIsNotAnArchiveAtAll() {
        val notAnArchive = workspace.resolve("photo.jpg")
        Files.write(notAnArchive, ArchiveEditor.bytes("just a jpeg, honestly"))
        assertContains(restoring(notAnArchive).userMessage, "not a Keeply backup")
    }

    @Test
    fun refusesAFileWhoseContentsDoNotMatchTheManifest() = runBlocking {
        // A backup someone edited after it was written, or one that was corrupted
        // in transit.
        val source = workspace.resolve("source.keeplybackup")
        TestLibrary(workspace.resolve("lib")).use { library ->
            library.addPurchaseWithReceipt("Thing")
            BackupWriter(library.directory, "1.0.0").write(library.store, source)
        }
        val tampered = workspace.resolve("tampered.keeplybackup")
        ArchiveEditor.rewriteManifest(source, tampered) { manifest ->
            manifest.copy(entries = manifest.entries.map { it.copy(sha256 = "0".repeat(64)) })
        }

        assertContains(restoring(tampered).userMessage, "damaged")
        assertWroteNothing()
    }

    @Test
    fun refusesAPathWithNamesPaddedToLookLikeOthers() {
        val hostile = archive(
            "padded.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "files/receipts/ab /one.png" to ArchiveEditor.bytes("x"),
        )
        assertContains(restoring(hostile).userMessage, "padded names")
        assertWroteNothing()
    }

    @Test
    fun refusesAnAbsurdlyLongPath() {
        val hostile = archive(
            "long.zip",
            BackupManifest.MANIFEST_PATH to ArchiveEditor.manifestBytes(),
            "files/receipts/" + "a".repeat(300) + ".png" to ArchiveEditor.bytes("x"),
        )
        assertContains(restoring(hostile).userMessage, "longer than any Keeply writes")
        assertWroteNothing()
    }
}
