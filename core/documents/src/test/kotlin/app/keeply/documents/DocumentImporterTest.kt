package app.keeply.documents

import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentKind
import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptPdfRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentImporterTest {
    private val workspace: Path = createTempDirectory("keeply-import")
    private val incoming: Path = Files.createDirectories(workspace.resolve("incoming"))
    private val directory = DataDirectory(workspace.resolve("keeply-data")).create()
    private val importer = DocumentImporter(directory)
    private val generator = ReceiptGenerator(seed = 99)

    @AfterTest
    fun cleanUp() {
        workspace.toFile().deleteRecursively()
    }

    private fun drop(name: String, bytes: ByteArray): Path = incoming.resolve(name).also { Files.write(it, bytes) }

    private fun receiptPng(): Path = drop("receipt.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(generator.spec())))

    @Test
    fun takesAPhotographOfAReceipt() {
        val source = receiptPng()
        val imported = importer.importBlocking(source, DocumentKind.RECEIPT)

        assertEquals(DocumentFormat.PNG, imported.document.format)
        assertEquals("receipt.png", imported.document.originalFileName)
        assertEquals(Files.size(source), imported.document.byteSize)
        assertTrue(Files.exists(directory.resolve(imported.document.relativePath)))
    }

    @Test
    fun leavesTheOriginalFileExactlyWhereItWas() {
        // Keeply copies. Someone's Downloads folder is not Keeply's to rearrange.
        val source = receiptPng()
        val before = Files.readAllBytes(source)
        importer.importBlocking(source, DocumentKind.RECEIPT)

        assertTrue(Files.exists(source))
        assertContentEquals(before, Files.readAllBytes(source))
    }

    @Test
    fun storesFilesUnderGeneratedNamesThatRevealNothing() {
        val source = drop("Best Buy receipt - headphones - 2026-09-12.png", Files.readAllBytes(receiptPng()))
        val imported = importer.importBlocking(source, DocumentKind.RECEIPT)

        val path = imported.document.relativePath
        assertFalse(path.contains("Best Buy", ignoreCase = true), "stored path leaked the file name: $path")
        assertFalse(path.contains("headphones", ignoreCase = true))
        assertContains(path, imported.document.id.value)
    }

    @Test
    fun fingerprintsTheFileForDuplicateDetection() {
        val source = receiptPng()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(source))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, importer.importBlocking(source, DocumentKind.RECEIPT).document.sha256)
    }

    @Test
    fun readsAPdfThatAlreadyHasItsTextAndSkipsOcrEntirely() {
        val spec = generator.spec()
        val source = drop("emailed.pdf", ReceiptPdfRenderer.withTextLayer(spec))

        val imported = importer.importBlocking(source, DocumentKind.RECEIPT)
        val text = assertNotNull(imported.embeddedText, "an emailed receipt has a text layer")
        assertContains(text, spec.receiptNumber)
        assertEquals(1, imported.document.pageCount)
    }

    @Test
    fun knowsWhenAPdfIsOnlyAPhotographAndWillNeedOcr() {
        val source = drop("scan.pdf", ReceiptPdfRenderer.asScan(generator.spec(), CaptureConditions.PHOTOGRAPHED))
        val imported = importer.importBlocking(source, DocumentKind.RECEIPT)
        assertNull(imported.embeddedText)
    }

    @Test
    fun identifiesFilesByTheirContentNotTheirName() {
        // The extension is a claim by whoever made the file. A renamed executable
        // must not reach a PDF parser.
        val png = ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(generator.spec()))
        val source = drop("totally-a-receipt.pdf", png)

        val imported = importer.importBlocking(source, DocumentKind.RECEIPT)
        assertEquals(DocumentFormat.PNG, imported.document.format)
        assertTrue(imported.extensionMismatched)
        assertTrue(imported.document.relativePath.endsWith(".png"))
    }

    @Test
    fun refusesAnythingThatIsNotAReceipt() {
        val executable = drop("payload.png", byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(64))
        val failure = assertFailsWith<ImportException> { importer.importBlocking(executable, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.UnsupportedType)
        assertContains(failure.message.orEmpty(), "JPG, PNG or PDF")
    }

    @Test
    fun refusesAFileLargerThanItWillEverNeed() {
        val small = DocumentImporter(directory, ImportLimits(maxFileBytes = 1_024))
        val source = receiptPng()
        val failure = assertFailsWith<ImportException> { small.importBlocking(source, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.TooLarge)
        assertContains(failure.message.orEmpty(), "smaller size")
    }

    @Test
    fun refusesAnImageThatClaimsImpossibleDimensions() {
        // A decompression bomb: a tiny file that would allocate gigabytes if decoded.
        // The check reads the header only, so nothing is ever decoded.
        val bomb = pngHeaderClaiming(width = 60_000, height = 60_000)
        val source = drop("bomb.png", bomb)

        val failure = assertFailsWith<ImportException> { importer.importBlocking(source, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.TooLarge)
        assertContains(failure.message.orEmpty(), "60000x60000")
    }

    @Test
    fun refusesAPdfWithMorePagesThanAnyReceipt() {
        val short = DocumentImporter(directory, ImportLimits(maxPdfPages = 1))
        val longText = buildString { repeat(400) { appendLine("LINE $it   $12.34") } }
        val source = drop("long.pdf", ReceiptPdfRenderer.withTextLayer(longText))

        val failure = assertFailsWith<ImportException> { short.importBlocking(source, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.TooManyPages)
    }

    @Test
    fun refusesADamagedPdfWithSomethingUseful() {
        val source = drop("broken.pdf", "%PDF-1.7\nthis is not actually a pdf".toByteArray())
        val failure = assertFailsWith<ImportException> { importer.importBlocking(source, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.Damaged)
        assertContains(failure.message.orEmpty(), "damaged")
    }

    @Test
    fun refusesAFolder() {
        val failure = assertFailsWith<ImportException> { importer.importBlocking(incoming, DocumentKind.RECEIPT) }
        assertTrue(failure.rejection is ImportRejection.NotAFile)
        assertContains(failure.message.orEmpty(), "folder")
    }

    @Test
    fun refusesAFileThatIsNotThere() {
        val failure = assertFailsWith<ImportException> {
            importer.importBlocking(incoming.resolve("nothing.png"), DocumentKind.RECEIPT)
        }
        assertTrue(failure.rejection is ImportRejection.NotAFile)
    }

    @Test
    fun leavesNoHalfWrittenFileBehindWhenAnImportFails() {
        val tiny = DocumentImporter(directory, ImportLimits(maxFileBytes = 1_024))
        runCatching { tiny.importBlocking(receiptPng(), DocumentKind.RECEIPT) }

        val leftovers = Files.walk(directory.receipts).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList()
        }
        assertEquals(emptyList(), leftovers, "a failed import should leave nothing behind")
    }

    @Test
    fun filesEachKindWhereItBelongs() {
        val png = receiptPng()
        assertContains(importer.importBlocking(png, DocumentKind.RECEIPT).document.relativePath, "receipts/")
        assertContains(importer.importBlocking(png, DocumentKind.MANUAL).document.relativePath, "manuals/")
        assertContains(importer.importBlocking(png, DocumentKind.PRODUCT_PHOTO).document.relativePath, "photos/")
    }

    /** A valid PNG header declaring a size no camera produces. Never decoded. */
    private fun pngHeaderClaiming(width: Int, height: Int): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf('I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()))
            write(intBytes(width))
            write(intBytes(height))
            write(byteArrayOf(8, 2, 0, 0, 0))
        }.toByteArray()
        val crc = java.util.zip.CRC32().apply { update(ihdrData) }.value.toInt()
        return java.io.ByteArrayOutputStream().apply {
            write(signature)
            write(intBytes(ihdrData.size - 4))
            write(ihdrData)
            write(intBytes(crc))
        }.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual), "file contents changed")
    }
}
