package app.keeply.documents

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File

/** What Keeply learned by opening a PDF, without trusting anything inside it. */
public data class PdfSummary(
    val pageCount: Int,
    val isEncrypted: Boolean,
    /** Text already in the file. Null when there is no usable text layer. */
    val embeddedText: String?,
) {
    /** True when the PDF can be read directly and OCR would be a waste of time. */
    public val hasUsableTextLayer: Boolean get() = embeddedText != null
}

/**
 * Reads PDFs carefully.
 *
 * An imported PDF is untrusted: it can be damaged, enormous, encrypted, or built
 * to make a parser work very hard. Everything here fails into a plain-language
 * rejection rather than a stack trace, and nothing in a PDF is ever executed or
 * fetched.
 */
public object PdfDocument {
    private val log = LoggerFactory.getLogger(PdfDocument::class.java)

    /**
     * How much text a PDF needs before Keeply treats it as having a real text layer.
     *
     * Scanners sometimes embed a handful of stray characters, and running the
     * deterministic parser over six characters of noise is worse than running OCR.
     */
    private const val MIN_USABLE_TEXT_CHARS = 40

    public fun summarise(file: File, limits: ImportLimits = ImportLimits.DEFAULT): PdfSummary = open(file).use { document ->
        if (document.isEncrypted) {
            return PdfSummary(document.numberOfPages, isEncrypted = true, embeddedText = null)
        }
        val pages = document.numberOfPages
        if (pages > limits.maxPdfPages) {
            throw ImportException(
                ImportRejection.TooManyPages(
                    "That PDF has $pages pages. Keeply takes up to ${limits.maxPdfPages}. " +
                        "If it is a long manual, try splitting it or attaching just the pages you need.",
                ),
            )
        }
        PdfSummary(
            pageCount = pages,
            isEncrypted = false,
            embeddedText = extractText(document)?.takeIf { usable(it) },
        )
    }

    /** Renders one page for OCR. Only reached when a PDF has no text of its own. */
    public fun renderPage(file: File, pageIndex: Int, dpi: Float = OCR_DPI): BufferedImage = open(file).use { document ->
        require(pageIndex in 0 until document.numberOfPages) {
            "Page ${pageIndex + 1} does not exist in this PDF"
        }
        try {
            PDFRenderer(document).renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
        } catch (e: Exception) {
            log.warn("Could not render page {} of {}", pageIndex, file.name, e)
            throw ImportException(
                ImportRejection.Damaged(
                    "Keeply could not read page ${pageIndex + 1} of that PDF. " +
                        "It may be damaged. Try re-saving or re-exporting it.",
                ),
            )
        }
    }

    private fun open(file: File): PDDocument = try {
        Loader.loadPDF(file)
    } catch (e: java.io.IOException) {
        log.warn("Could not open PDF {}", file.name, e)
        throw ImportException(
            ImportRejection.Damaged(
                "Keeply could not open that PDF. It may be damaged or only partly downloaded.",
            ),
        )
    } catch (e: IllegalArgumentException) {
        log.warn("Malformed PDF {}", file.name, e)
        throw ImportException(ImportRejection.Damaged("That PDF is not readable."))
    }

    private fun extractText(document: PDDocument): String? = try {
        PDFTextStripper().apply { sortByPosition = true }.getText(document)
    } catch (e: Exception) {
        // A broken text layer is a reason to fall back to OCR, not a reason to fail.
        log.debug("PDF text extraction failed; falling back to OCR", e)
        null
    }

    private fun usable(text: String): Boolean = text.count { it.isLetterOrDigit() } >= MIN_USABLE_TEXT_CHARS

    /** 300 dpi is where Tesseract stops losing small print on till receipts. */
    public const val OCR_DPI: Float = 300f
}
