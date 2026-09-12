package app.keeply.fixtures

import app.keeply.domain.CurrencyCode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptRenderingTest {
    private val generator = ReceiptGenerator(seed = 2_026)

    @Test
    fun textLayoutContainsTheFactsAnExtractorNeeds() {
        generator.corpus(20).forEach { spec ->
            val text = ReceiptTextRenderer.render(spec)
            assertContains(text, spec.receiptNumber)
            val printedTotal = ReceiptTextRenderer.amountText(spec, spec.totalMinor)
            assertTrue(
                text.contains(printedTotal),
                "the total $printedTotal should appear in a ${spec.layout} receipt",
            )
            assertTrue(text.lines().size > MIN_LINES, "a receipt should not be one line")
        }
    }

    @Test
    fun someTillsGroupThousandsAndSomeDoNot() {
        // Extraction has to cope with both, so the corpus contains both.
        val specs = generator.corpus(40)
        assertTrue(specs.any { it.groupsThousands })
        assertTrue(specs.any { !it.groupsThousands })
        val grouped = specs.first { it.groupsThousands && it.currency == CurrencyCode.USD }
        assertEquals("$1,019.72", ReceiptTextRenderer.amountText(grouped, 101_972))
        assertEquals("$1019.72", ReceiptTextRenderer.amountText(grouped.copy(groupsThousands = false), 101_972))
    }

    @Test
    fun everyLayoutNamesTheShop() {
        ReceiptLayout.entries.forEach { layout ->
            val spec = generator.spec(layout = layout)
            val text = ReceiptTextRenderer.render(spec).uppercase()
            assertContains(text, spec.merchantName.uppercase())
        }
    }

    @Test
    fun rendersAReadableImageWithoutAnyFontsInstalled() {
        // CI runners have no fonts, so the renderer uses the JVM's logical monospaced
        // font and must produce the same result everywhere.
        val image = ReceiptImageRenderer.render(generator.spec(layout = ReceiptLayout.CLASSIC_TILL))
        assertTrue(image.width > MIN_DIMENSION)
        assertTrue(image.height > MIN_DIMENSION)

        var darkPixels = 0
        for (y in 0 until image.height step SAMPLE_STEP) {
            for (x in 0 until image.width step SAMPLE_STEP) {
                if ((image.getRGB(x, y) and 0xFF) < DARK_THRESHOLD) darkPixels++
            }
        }
        assertTrue(darkPixels > 0, "the rendered receipt should have ink on it")
    }

    @Test
    fun capturingBadlyChangesTheImageInTheWayItClaims() {
        val spec = generator.spec(layout = ReceiptLayout.CLASSIC_TILL)
        val clean = ReceiptImageRenderer.render(spec, CaptureConditions.CLEAN)
        val photographed = ReceiptImageRenderer.render(spec, CaptureConditions.PHOTOGRAPHED)
        val faded = ReceiptImageRenderer.render(spec, CaptureConditions.FADED)

        assertTrue(photographed.width > clean.width, "a photo includes the surface around the paper")
        assertTrue(meanBrightness(faded) > meanBrightness(clean), "faded thermal paper is lighter")
    }

    @Test
    fun aTextLayerPdfCanBeReadWithoutOcr() {
        val spec = generator.spec(layout = ReceiptLayout.INVOICE)
        val pdf = ReceiptPdfRenderer.withTextLayer(spec)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertContains(text, spec.receiptNumber)
            assertContains(text, spec.merchantName)
        }
    }

    @Test
    fun aScannedPdfHasNoTextLayerAtAll() {
        // This is the distinction the import pipeline uses to decide whether OCR is
        // needed, so it has to actually hold.
        val pdf = ReceiptPdfRenderer.asScan(generator.spec())
        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document).trim()
            assertEquals("", text)
            assertEquals(1, document.numberOfPages)
        }
    }

    @Test
    fun producesRealImageFiles() {
        val image = ReceiptImageRenderer.render(generator.spec())
        val png = ReceiptImageRenderer.toPng(image)
        val jpeg = ReceiptImageRenderer.toJpeg(image)

        assertEquals(PNG_MAGIC.toList(), png.take(PNG_MAGIC.size))
        assertEquals(listOf(0xFF.toByte(), 0xD8.toByte()), jpeg.take(2))
    }

    private fun meanBrightness(image: java.awt.image.BufferedImage): Double {
        var sum = 0L
        var count = 0
        for (y in 0 until image.height step SAMPLE_STEP) {
            for (x in 0 until image.width step SAMPLE_STEP) {
                sum += image.getRGB(x, y) and 0xFF
                count++
            }
        }
        return sum.toDouble() / count
    }

    private companion object {
        const val MIN_LINES = 5
        const val MIN_DIMENSION = 200
        const val SAMPLE_STEP = 3
        const val DARK_THRESHOLD = 100
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    }
}
