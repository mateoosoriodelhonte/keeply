package app.keeply.services

import app.keeply.backup.RestoreMode
import app.keeply.documents.DataDirectory
import app.keeply.domain.DocumentKind
import app.keeply.domain.FieldSource
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnStatus
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptLayout
import app.keeply.fixtures.ReceiptPdfRenderer
import app.keeply.reminders.Notifier
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole product, from a file on disk to a purchase someone can find again.
 *
 * Each of these is a thing a person actually does. If one of them breaks, Keeply
 * has failed at the job it exists for, whatever the unit tests say.
 */
class EndToEndTest {
    private val workspace: Path = createTempDirectory("keeply-e2e")
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    /**
     * A fresh generator per call, seeded by the caller.
     *
     * A shared generator advances with every use, which made each test depend on
     * the order the others ran in. That is a slow way to learn that a failure is
     * not where it appears to be.
     */
    private fun spec(seed: Long, layout: ReceiptLayout = ReceiptLayout.CLASSIC_TILL, daysAgo: Long = 3) =
        ReceiptGenerator(seed).spec(layout = layout, date = today.minusDays(daysAgo))

    private val keeply = Keeply.open(
        directory = DataDirectory(workspace.resolve("keeply-data")),
        notifier = Notifier.disabled,
        clock = clock,
    )

    @AfterTest
    fun cleanUp() {
        keeply.close()
        workspace.toFile().deleteRecursively()
    }

    private fun drop(name: String, bytes: ByteArray): Path = workspace.resolve(name).also { Files.write(it, bytes) }

    @Test
    fun aPersonImportsAPhotoOfAReceiptAndFindsItAgain() = runBlocking {
        val spec = spec(601L, layout = ReceiptLayout.CLASSIC_TILL, daysAgo = 3)
        val file = drop(
            "receipt.png",
            ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec, CaptureConditions.PHOTOGRAPHED)),
        )

        val stages = mutableListOf<String>()
        val outcome = keeply.imports.import(file) { stages += it::class.simpleName.orEmpty() }

        // The progress a person watches is real, not decorative.
        assertContains(stages, "Saving")
        assertContains(stages, "Preparing")
        assertContains(stages, "Finished")

        // It read the receipt.
        assertEquals(FieldSource.OCR, outcome.textSource)
        assertNotNull(outcome.ocr)
        assertTrue(assertNotNull(outcome.ocr).meanConfidence > 50)
        assertEquals(spec.merchantName, outcome.draft.merchantName?.value)
        assertEquals(spec.totalMinor, outcome.draft.total?.value?.amountMinor)

        // A person checks it and saves.
        val proposal = keeply.purchases.proposeFrom(outcome.draft, outcome.document.id)
        val saved = keeply.purchases.save(
            proposal.copy(
                productName = "Wireless Headphones",
                returnPolicy = ReturnPolicy.Days(30),
                returnPolicySource = ReturnPolicySource.USER_ENTERED,
                warrantyTerm = WarrantyTerm.Months(24),
                warrantyProvenance = WarrantyProvenance.USER_ENTERED,
            ),
            receiptText = outcome.text,
        )

        // And finds it later, by a word that was only ever on the receipt.
        assertEquals(listOf("Wireless Headphones"), keeply.library.search("headphones").purchases.map { it.productName })
        val byShop = keeply.library.search(spec.merchantName.split(" ").first())
        assertEquals(1, byShop.purchases.size)

        // With the return window and warranty worked out.
        val loaded = assertNotNull(keeply.store.purchases.get(saved.id))
        assertEquals(today.minusDays(3).plusDays(30), loaded.returnWindow.deadline)
        assertEquals(ReturnStatus.RETURNABLE, loaded.returnStatusOn(today))
        assertEquals(today.minusDays(3).plusMonths(24), loaded.warranty.endDate)

        // And the original receipt is still there, untouched.
        val receipt = keeply.directory.resolve(
            assertNotNull(loaded.receiptDocumentId).let {
                assertNotNull(keeply.store.documents.get(it)).relativePath
            },
        )
        assertTrue(Files.isRegularFile(receipt))
        assertEquals(Files.size(file), Files.size(receipt))
    }

    @Test
    fun anEmailedPdfSkipsOcrEntirely() = runBlocking {
        val spec = spec(602L, layout = ReceiptLayout.INVOICE, daysAgo = 1)
        val file = drop("order.pdf", ReceiptPdfRenderer.withTextLayer(spec))

        val outcome = keeply.imports.import(file)

        assertEquals(FieldSource.PDF_TEXT, outcome.textSource)
        assertEquals(null, outcome.ocr, "a PDF with its own text must not be sent through OCR")
        assertEquals(spec.totalMinor, outcome.draft.total?.value?.amountMinor)
        assertEquals(spec.receiptNumber, outcome.draft.receiptNumber?.value)
    }

    @Test
    fun aScannedPdfIsReadWithOcr() = runBlocking {
        val spec = spec(603L, layout = ReceiptLayout.CLASSIC_TILL, daysAgo = 2)
        val file = drop("scan.pdf", ReceiptPdfRenderer.asScan(spec))

        val outcome = keeply.imports.import(file)

        assertEquals(FieldSource.OCR, outcome.textSource)
        assertNotNull(outcome.ocr)
        assertEquals(spec.totalMinor, outcome.draft.total?.value?.amountMinor)
    }

    @Test
    fun importingTheSameReceiptTwiceAsksRatherThanDuplicating() = runBlocking {
        val spec = spec(604L, daysAgo = 5)
        val bytes = ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))

        val first = keeply.imports.import(drop("first.png", bytes))
        keeply.purchases.save(keeply.purchases.proposeFrom(first.draft, first.document.id).copy(productName = "Thing"))

        val second = keeply.imports.import(drop("again.png", bytes))
        val duplicate = assertNotNull(second.likelyDuplicate, "the same file should be spotted")
        assertEquals("This is the same file you already imported.", duplicate.explain())

        // Nothing was removed, and nothing was saved without being asked.
        assertEquals(1L, keeply.store.purchases.count())
    }

    @Test
    fun theShopIsRememberedSoTheNextReceiptFromItIsRecognised() = runBlocking {
        val spec = spec(605L, layout = ReceiptLayout.COLUMNAR, daysAgo = 10)
        val first = keeply.imports.import(
            drop("one.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))),
        )
        keeply.purchases.save(
            keeply.purchases.proposeFrom(first.draft, first.document.id).copy(productName = "First thing"),
        )

        val second = keeply.imports.import(
            drop("two.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec.copy(receiptNumber = "TXN-999")))),
        )
        assertNotNull(second.draft.matchedMerchantId, "the shop should have been recognised the second time")
        assertEquals(false, second.draft.merchantName?.needsReview)
    }

    @Test
    fun aBadPhotoIsStillReadAndTheAdviceIsHonest() = runBlocking {
        val spec = spec(606L, daysAgo = 1)
        val file = drop(
            "blurry.png",
            ReceiptImageRenderer.toPng(
                ReceiptImageRenderer.render(spec, CaptureConditions(blurRadius = 5, noise = 0.1)),
            ),
        )
        val outcome = keeply.imports.import(file)

        // Keeply tries anyway, and says what would have helped.
        assertNotNull(outcome.advice)
        assertContains(assertNotNull(outcome.advice), "blurry")
        assertNotNull(outcome.ocr)
    }

    @Test
    fun everythingSurvivesABackupAndRestore() = runBlocking {
        val spec = spec(607L, daysAgo = 4)
        val imported = keeply.imports.import(
            drop("backupme.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))),
        )
        val saved = keeply.purchases.save(
            keeply.purchases.proposeFrom(imported.draft, imported.document.id).copy(
                productName = "Espresso Machine",
                returnPolicy = ReturnPolicy.Days(60),
                returnPolicySource = ReturnPolicySource.USER_ENTERED,
            ),
            receiptText = imported.text,
        )

        val archive = workspace.resolve("library.keeplybackup")
        keeply.backups.export(archive)

        val restoredRoot = workspace.resolve("restored")
        app.keeply.backup.BackupReader().restore(archive, DataDirectory(restoredRoot), RestoreMode.ONLY_IF_EMPTY)

        Keeply.open(DataDirectory(restoredRoot), Notifier.disabled, clock).use { restored ->
            val loaded = assertNotNull(restored.store.purchases.get(saved.id))
            assertEquals("Espresso Machine", loaded.productName)
            assertEquals(saved.returnWindow.deadline, loaded.returnWindow.deadline)
            assertEquals(1, restored.library.search("espresso").purchases.size)

            val document = assertNotNull(restored.store.documents.get(assertNotNull(loaded.receiptDocumentId)))
            assertTrue(Files.isRegularFile(restored.directory.resolve(document.relativePath)))
        }
    }

    @Test
    fun aPersonCanTakeTheirDataWithThem() = runBlocking {
        val spec = spec(608L, daysAgo = 6)
        val imported = keeply.imports.import(
            drop("export.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))),
        )
        keeply.purchases.save(
            keeply.purchases.proposeFrom(imported.draft, imported.document.id).copy(
                productName = "Desk Chair",
                returnPolicy = ReturnPolicy.Days(60),
                returnPolicySource = ReturnPolicySource.USER_ENTERED,
            ),
        )

        val csv = keeply.backups.exportCsv(today)
        assertContains(csv, "Desk Chair")
        assertContains(csv, "returnable")

        val json = keeply.backups.exportJson(today)
        assertContains(json, "\"product\": \"Desk Chair\"")
    }

    @Test
    fun theDemoLibraryShowsWhatKeeplyIsFor() {
        val installed = keeply.demo.install(today)
        assertTrue(installed >= 6, "the demo should feel like a real library, had $installed")

        // The home screen has something to say on the day the demo is installed.
        assertTrue(keeply.library.needsAttention().isNotEmpty())
        assertTrue(keeply.library.recent().isNotEmpty())
        assertTrue(keeply.demo.isInstalled())

        // And it all comes out again, leaving nothing of its own behind.
        keeply.demo.clear()
        assertEquals(0L, keeply.store.purchases.count())
        assertEquals(false, keeply.demo.isInstalled())
    }

    @Test
    fun clearingDemoDataNeverTouchesSomethingReal() = runBlocking {
        val spec = spec(609L, daysAgo = 7)
        val imported = keeply.imports.import(
            drop("real.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))),
        )
        keeply.purchases.save(
            keeply.purchases.proposeFrom(imported.draft, imported.document.id).copy(productName = "A Real Purchase"),
        )
        keeply.demo.install(today)

        keeply.demo.clear()

        assertEquals(listOf("A Real Purchase"), keeply.store.purchases.all().map { it.productName })
    }

    @Test
    fun theStorageScreenOnlyEverOffersToDeleteWhatCanBeRebuilt() = runBlocking {
        val spec = spec(610L, daysAgo = 8)
        keeply.imports.import(drop("storage.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))))

        val report = keeply.storage.report()
        val rebuildable = report.lines.filter { it.rebuildable }.map { it.label }
        assertEquals(setOf("Thumbnails", "Processed images"), rebuildable.toSet())
        assertTrue(report.lines.single { it.label == "Receipts" }.bytes > 0)
        assertEquals(false, report.lines.single { it.label == "Receipts" }.rebuildable)

        val receiptBytesBefore = keeply.storage.report().lines.single { it.label == "Receipts" }.bytes
        keeply.storage.clearRebuildable()
        assertEquals(
            receiptBytesBefore,
            keeply.storage.report().lines.single { it.label == "Receipts" }.bytes,
            "clearing rebuildable data must not touch a receipt",
        )
    }

    @Test
    fun attachingAManualToAPurchase() = runBlocking {
        val spec = spec(611L, daysAgo = 9)
        val imported = keeply.imports.import(
            drop("appliance.png", ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))),
        )
        val saved = keeply.purchases.save(
            keeply.purchases.proposeFrom(imported.draft, imported.document.id).copy(productName = "Blender"),
        )

        val manual = keeply.imports.import(
            drop("manual.pdf", ReceiptPdfRenderer.withTextLayer("BLENDER 1200W\nOperating instructions")),
            kind = DocumentKind.MANUAL,
        )
        keeply.purchases.attach(saved.id, manual.document.id, app.keeply.data.AttachmentRole.MANUAL)

        val loaded = assertNotNull(keeply.store.purchases.get(saved.id))
        assertEquals(listOf(manual.document.id), loaded.manualDocumentIds)
        assertContains(manual.document.relativePath, "manuals/")
    }
}
