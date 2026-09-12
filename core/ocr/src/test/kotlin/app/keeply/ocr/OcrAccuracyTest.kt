package app.keeply.ocr

import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptLayout
import app.keeply.fixtures.ReceiptTextRenderer
import app.keeply.imaging.PreprocessOptions
import app.keeply.imaging.ReceiptPreprocessor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What Keeply actually reads, measured against receipts whose exact contents are
 * known.
 *
 * The thresholds here come from running this, not from a number that sounded
 * about right. They are set a little below the measured figures so ordinary
 * variation does not fail the build, but close enough that a real regression does.
 * The numbers quoted in `docs/OCR_PIPELINE.md` come from this test.
 */
class OcrAccuracyTest {
    private val generator = ReceiptGenerator(seed = 4242)
    private val preprocessor = ReceiptPreprocessor()

    @Test
    fun readsEveryReceiptLayoutAlmostPerfectly() {
        val report = StringBuilder("layout,similarity,confidence,millis\n")
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            ReceiptLayout.entries.forEach { layout ->
                val spec = generator.spec(layout = layout)
                val expected = ReceiptTextRenderer.render(spec)
                val result = engine.read(ReceiptImageRenderer.render(spec))
                val similarity = OcrTestSupport.similarity(expected, result.text)
                report.append(
                    "%s,%.4f,%.1f,%d\n".format(layout, similarity, result.meanConfidence, result.durationMillis),
                )
                assertTrue(similarity > CLEAN_FLOOR, "$layout read at $similarity, below $CLEAN_FLOOR")
            }
        }
        println(report)
    }

    @Test
    fun findsTheTotalOnEveryLayout() {
        // The one field a person will not forgive Keeply for losing.
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            ReceiptLayout.entries.forEach { layout ->
                val spec = generator.spec(layout = layout)
                val printedTotal = ReceiptTextRenderer.amountText(spec, spec.totalMinor)
                val text = engine.read(ReceiptImageRenderer.render(spec)).text
                assertTrue(text.contains(printedTotal), "$layout lost its total ($printedTotal)")
            }
        }
    }

    @Test
    fun copesWithHowReceiptsActuallyArrive() {
        val spec = generator.spec(layout = ReceiptLayout.CLASSIC_TILL)
        val expected = ReceiptTextRenderer.render(spec)
        val report = StringBuilder("condition,similarity\n")

        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            mapOf(
                "clean" to (CaptureConditions.CLEAN to 0.95),
                "faded" to (CaptureConditions.FADED to 0.95),
                "photographed" to (CaptureConditions.PHOTOGRAPHED to 0.90),
                "blurry" to (CaptureConditions.BLURRY to 0.85),
            ).forEach { (name, setup) ->
                val (conditions, floor) = setup
                val image = ReceiptImageRenderer.render(spec, conditions)
                val prepared = preprocessor.prepare(image, PreprocessOptions.DEFAULT).image
                val similarity = OcrTestSupport.similarity(expected, engine.read(prepared).text)
                report.append("%s,%.4f\n".format(name, similarity))
                assertTrue(similarity > floor, "$name read at $similarity, below $floor")
            }
        }
        println(report)
    }

    @Test
    fun reportsLowConfidenceOnWordsItGuessedAt() {
        val spec = generator.spec(layout = ReceiptLayout.CLASSIC_TILL)
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            val clean = engine.read(ReceiptImageRenderer.render(spec))
            val awful = engine.read(
                ReceiptImageRenderer.render(spec, CaptureConditions(blurRadius = 7, noise = 0.3)),
            )
            assertTrue(
                awful.meanConfidence < clean.meanConfidence,
                "a ruined photo should report lower confidence than a clean one",
            )
            assertTrue(clean.words.isNotEmpty(), "word boxes are what the debug view draws")
            assertTrue(clean.words.all { it.box.width > 0 && it.box.height > 0 })
        }
    }

    private companion object {
        /** Measured 0.986 to 1.000 across the four layouts. */
        const val CLEAN_FLOOR = 0.95
    }
}
