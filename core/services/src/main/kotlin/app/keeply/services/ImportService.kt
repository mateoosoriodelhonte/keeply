package app.keeply.services

import app.keeply.data.KeeplyStore
import app.keeply.documents.DataDirectory
import app.keeply.documents.DocumentImporter
import app.keeply.documents.ImportedDocument
import app.keeply.documents.PdfDocument
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentKind
import app.keeply.domain.DocumentText
import app.keeply.domain.DuplicateCandidate
import app.keeply.domain.DuplicateDetector
import app.keeply.domain.DuplicateFingerprint
import app.keeply.domain.FieldSource
import app.keeply.domain.Merchant
import app.keeply.domain.PurchaseDraft
import app.keeply.domain.StoredDocument
import app.keeply.extraction.ExtractionInput
import app.keeply.extraction.ExtractionNote
import app.keeply.extraction.ReceiptExtractor
import app.keeply.imaging.ImageAssessment
import app.keeply.imaging.ImageQuality
import app.keeply.imaging.PreprocessOptions
import app.keeply.imaging.PreprocessResult
import app.keeply.imaging.Quadrilateral
import app.keeply.imaging.ReceiptPreprocessor
import app.keeply.imaging.Thumbnailer
import app.keeply.ocr.OcrProgress
import app.keeply.ocr.OcrResult
import app.keeply.ocr.OcrService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.coroutines.coroutineContext

/** What the import screen shows while it works. */
public sealed interface ImportStage {
    public data object Saving : ImportStage
    public data object Preparing : ImportStage
    public data class Reading(val page: Int, val pages: Int) : ImportStage
    public data object Understanding : ImportStage
    public data object Finished : ImportStage
}

/** Everything the review screen needs, and everything the debug view shows. */
public data class ImportOutcome(
    val document: StoredDocument,
    val draft: PurchaseDraft,
    val notes: List<ExtractionNote>,
    /** The text that was read, whether from the PDF or from OCR. */
    val text: String,
    val textSource: FieldSource,
    /** Null when the PDF carried its own text and OCR was skipped. */
    val ocr: OcrResult?,
    val preprocessing: PreprocessResult?,
    val imageAssessment: ImageAssessment?,
    val duplicates: List<DuplicateCandidate>,
    /** Set when the file's extension disagreed with its actual contents. */
    val extensionMismatched: Boolean,
) {
    /** A sentence about the photograph, or null when it was fine. */
    public val advice: String? get() = imageAssessment?.advice()

    public val likelyDuplicate: DuplicateCandidate? get() = duplicates.firstOrNull()
}

/**
 * Everything that happens between dropping a receipt on Keeply and being shown
 * what it read.
 *
 * The stages are deliberately visible. A person watching a progress bar should be
 * able to tell the difference between "saving your file" and "reading the text",
 * because those take very different amounts of time and failing at one means
 * something different from failing at the other.
 *
 * OCR is skipped entirely when a PDF carries its own text layer. Running it anyway
 * would be slower and worse.
 */
public class ImportService(
    private val store: KeeplyStore,
    private val directory: DataDirectory,
    private val importer: DocumentImporter,
    private val ocr: OcrService,
    private val preprocessor: ReceiptPreprocessor = ReceiptPreprocessor(),
    private val extractor: ReceiptExtractor = ReceiptExtractor(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(ImportService::class.java)

    public suspend fun import(
        source: Path,
        kind: DocumentKind = DocumentKind.RECEIPT,
        defaultCurrency: CurrencyCode = CurrencyCode.USD,
        preferDayFirstDates: Boolean = false,
        manualOutline: Quadrilateral? = null,
        onStage: (ImportStage) -> Unit = {},
    ): ImportOutcome = withContext(dispatcher) {
        onStage(ImportStage.Saving)
        val imported = importer.importBlocking(source, kind)
        store.documents.insert(imported.document)
        coroutineContext.ensureActive()

        val embedded = imported.embeddedText
        val reading = when {
            // A PDF that carries its own text was never guessed at. Running OCR over
            // it anyway would be slower and give a worse answer.
            embedded != null -> ReadResult(embedded, FieldSource.PDF_TEXT, null, null, null)

            imported.document.format == DocumentFormat.PDF -> readScannedPdf(imported, onStage)

            else -> readImage(imported, manualOutline, onStage)
        }

        // Derived text lives in its own table. Re-reading replaces it and never
        // touches the original document.
        store.documents.saveText(
            DocumentText(
                documentId = imported.document.id,
                text = reading.text,
                source = reading.source,
                meanConfidence = reading.ocr?.meanConfidence,
                extractedAt = Instant.now(clock),
                engine = reading.ocr?.engine ?: "pdf-text-layer",
                durationMillis = reading.ocr?.durationMillis ?: 0,
            ),
        )

        onStage(ImportStage.Understanding)
        val merchants: List<Merchant> = store.merchants.all()
        val outcome = extractor.extract(
            ExtractionInput(
                text = reading.text,
                source = reading.source,
                documentId = imported.document.id,
                meanOcrConfidence = reading.ocr?.meanConfidence,
                knownMerchants = merchants,
                defaultCurrency = defaultCurrency,
                preferDayFirstDates = preferDayFirstDates,
                today = LocalDate.now(clock),
            ),
        )

        val duplicates = findDuplicates(imported.document, outcome.draft)
        onStage(ImportStage.Finished)

        ImportOutcome(
            document = imported.document,
            draft = outcome.draft,
            notes = outcome.notes,
            text = reading.text,
            textSource = reading.source,
            ocr = reading.ocr,
            preprocessing = reading.preprocessing,
            imageAssessment = reading.assessment,
            duplicates = duplicates,
            extensionMismatched = imported.extensionMismatched,
        )
    }

    private data class ReadResult(
        val text: String,
        val source: FieldSource,
        val ocr: OcrResult?,
        val preprocessing: PreprocessResult?,
        val assessment: ImageAssessment?,
    )

    private suspend fun readImage(imported: ImportedDocument, manualOutline: Quadrilateral?, onStage: (ImportStage) -> Unit): ReadResult {
        onStage(ImportStage.Preparing)
        val file = directory.resolve(imported.document.relativePath)
        val original = ImageIO.read(file.toFile())
            ?: return ReadResult("", FieldSource.OCR, null, null, null)

        val assessment = ImageQuality.assess(original)
        val prepared = preprocessor.prepare(original, PreprocessOptions.DEFAULT.copy(manualOutline = manualOutline))
        writeDerived(imported.document.id.value, prepared.image, directory.processed)
        writeDerived(imported.document.id.value, Thumbnailer.thumbnail(original), directory.thumbnails)

        val result = ocr.read(prepared.image) { progress ->
            if (progress is OcrProgress.Reading) onStage(ImportStage.Reading(progress.pageNumber, progress.pageCount))
        }
        return ReadResult(result.text, FieldSource.OCR, result, prepared, assessment)
    }

    private suspend fun readScannedPdf(imported: ImportedDocument, onStage: (ImportStage) -> Unit): ReadResult {
        onStage(ImportStage.Preparing)
        val file = directory.resolve(imported.document.relativePath).toFile()
        val pageCount = imported.document.pageCount ?: 1

        val pages = (0 until pageCount).map { index ->
            coroutineContext.ensureActive()
            val rendered = PdfDocument.renderPage(file, index)
            preprocessor.prepare(rendered, PreprocessOptions.DEFAULT).image
        }
        pages.firstOrNull()?.let { writeDerived(imported.document.id.value, Thumbnailer.thumbnail(it), directory.thumbnails) }

        val result = ocr.readPages(pages) { progress ->
            if (progress is OcrProgress.Reading) onStage(ImportStage.Reading(progress.pageNumber, progress.pageCount))
        }
        return ReadResult(result.text, FieldSource.OCR, result, null, null)
    }

    /** Derived images are written under the document's own id so they can be found and rebuilt. */
    private fun writeDerived(id: String, image: BufferedImage, into: Path) {
        runCatching {
            val target = into.resolve(id.take(2)).resolve("$id.jpg")
            Files.createDirectories(target.parent)
            Files.write(target, Thumbnailer.toJpeg(image))
        }.onFailure { log.warn("Could not write derived image for {}", id, it) }
    }

    private fun findDuplicates(document: StoredDocument, draft: PurchaseDraft): List<DuplicateCandidate> {
        val incoming = DuplicateFingerprint(
            purchaseId = null,
            fileSha256 = document.sha256,
            merchantKey = draft.merchantName?.value?.let(Merchant::normaliseName),
            purchaseDate = draft.purchaseDate?.value,
            total = draft.total?.value,
            receiptNumber = draft.receiptNumber?.value,
        )
        return DuplicateDetector.findAll(incoming, store.purchases.fingerprints())
    }
}
