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
 * Why the preprocessing defaults are what they are.
 *
 * This encodes the two conclusions that set them, so neither can quietly rot:
 * perspective correction is the step worth having, and binarising the image
 * ourselves makes Tesseract worse rather than better.
 *
 * It runs a real comparison rather than asserting a remembered number.
 */
class PreprocessingAblationTest {
    private val preprocessor = ReceiptPreprocessor()

    /**
     * Built once, so every variant is compared on the same receipts. Generating
     * fresh ones per variant would advance the shared generator and quietly turn
     * the comparison into noise.
     */
    private val specs = ReceiptGenerator(seed = 71).let { generator ->
        ReceiptLayout.entries.map { generator.spec(layout = it) }
    }

    private fun meanSimilarity(engine: TesseractEngine, conditions: CaptureConditions, options: PreprocessOptions): Double =
        specs.map { spec ->
            val expected = ReceiptTextRenderer.render(spec)
            val image = ReceiptImageRenderer.render(spec, conditions)
            OcrTestSupport.similarity(expected, engine.read(preprocessor.prepare(image, options).image).text)
        }.average()

    @Test
    fun perspectiveCorrectionIsWorthHaving() {
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            val without = meanSimilarity(
                engine,
                CaptureConditions.PHOTOGRAPHED,
                PreprocessOptions.DEFAULT.copy(correctPerspective = false),
            )
            val with = meanSimilarity(engine, CaptureConditions.PHOTOGRAPHED, PreprocessOptions.DEFAULT)
            println("photographed: without perspective %.4f, with %.4f".format(without, with))
            assertTrue(
                with > without,
                "flattening the page should help: $without without, $with with",
            )
        }
    }

    @Test
    fun binarisingOurselvesMakesItWorseSoTesseractDoesIt() {
        // The result that changed Keeply's defaults. Tesseract binarises internally
        // and does it better than a fixed block size can.
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            val greyscale = meanSimilarity(engine, CaptureConditions.PHOTOGRAPHED, PreprocessOptions.DEFAULT)
            val thresholded = meanSimilarity(
                engine,
                CaptureConditions.PHOTOGRAPHED,
                PreprocessOptions.DEFAULT.copy(threshold = true),
            )
            println("photographed: greyscale %.4f, thresholded %.4f".format(greyscale, thresholded))
            assertTrue(
                greyscale > thresholded,
                "thresholding should measure worse, which is why it is off: $greyscale vs $thresholded",
            )
        }
    }

    @Test
    fun theDefaultsBeatDoingEverything() {
        TesseractEngine(OcrTestSupport.tessdata).use { engine ->
            val defaults = meanSimilarity(engine, CaptureConditions.PHOTOGRAPHED, PreprocessOptions.DEFAULT)
            val everything = meanSimilarity(engine, CaptureConditions.PHOTOGRAPHED, PreprocessOptions.AGGRESSIVE)
            println("photographed: defaults %.4f, everything on %.4f".format(defaults, everything))
            assertTrue(defaults > everything, "$defaults should beat $everything")
        }
    }
}
