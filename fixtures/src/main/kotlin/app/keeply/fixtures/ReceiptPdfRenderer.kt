package app.keeply.fixtures

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream

/**
 * Builds the two kinds of PDF receipt Keeply has to tell apart.
 *
 * An emailed receipt has a real text layer and should never be sent through OCR;
 * a scan is a photograph in a PDF wrapper and has to be. The distinction is worth
 * testing because getting it wrong is slow rather than visibly broken.
 */
public object ReceiptPdfRenderer {

    private const val FONT_SIZE = 9f
    private const val LEADING = 11.5f
    private const val MARGIN = 40f

    /** A PDF with a genuine text layer, like a receipt emailed from an online order. */
    public fun withTextLayer(spec: ReceiptSpec): ByteArray = withTextLayer(ReceiptTextRenderer.render(spec))

    public fun withTextLayer(text: String): ByteArray {
        PDDocument().use { document ->
            val lines = text.lines()
            val font = PDType1Font(Standard14Fonts.FontName.COURIER)
            val linesPerPage = ((PDRectangle.LETTER.height - MARGIN * 2) / LEADING).toInt()

            lines.chunked(linesPerPage).forEach { pageLines ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, FONT_SIZE)
                    stream.setLeading(LEADING)
                    stream.newLineAtOffset(MARGIN, PDRectangle.LETTER.height - MARGIN)
                    pageLines.forEach { line ->
                        // Standard 14 fonts are WinAnsi only; a currency symbol outside
                        // that set would throw rather than draw.
                        stream.showText(line.map { if (it.code in 32..255) it else '?' }.joinToString(""))
                        stream.newLine()
                    }
                    stream.endText()
                }
            }
            return document.toByteArray()
        }
    }

    /** A PDF containing nothing but a photograph, like a phone scan. Needs OCR. */
    public fun asScan(spec: ReceiptSpec, conditions: CaptureConditions = CaptureConditions.CLEAN): ByteArray {
        val image = ReceiptImageRenderer.render(spec, conditions)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(image.width.toFloat(), image.height.toFloat()))
            document.addPage(page)
            val pdImage = JPEGFactory.createFromImage(document, image)
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(pdImage, 0f, 0f, image.width.toFloat(), image.height.toFloat())
            }
            return document.toByteArray()
        }
    }

    private fun PDDocument.toByteArray(): ByteArray = ByteArrayOutputStream().use { out ->
        save(out)
        out.toByteArray()
    }
}
