package app.keeply.imaging

import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptPreprocessorTest {
    private val generator = ReceiptGenerator(seed = 314)
    private val preprocessor = ReceiptPreprocessor()
    private val clean = ReceiptImageRenderer.render(generator.spec(layout = ReceiptLayout.CLASSIC_TILL))

    @Test
    fun findsThePaperInAPhotographTakenAtAnAngle() {
        val photograph = Distortions.photographAtAnAngle(clean)
        val outline = assertNotNull(
            DocumentEdgeDetector.detect(photograph),
            "the sheet of paper should be findable against a dark surface",
        )
        // The paper occupies most of the frame but not all of it.
        val frameArea = photograph.width.toDouble() * photograph.height
        val fraction = outline.area / frameArea
        assertTrue(fraction in 0.3..0.95, "outline covered $fraction of the frame")
    }

    @Test
    fun leavesACleanScanAloneRatherThanCroppingIntoIt() {
        // A flatbed scan is already the document. Cropping to a guess here would
        // throw away part of a receipt for no gain.
        assertNull(DocumentEdgeDetector.detect(clean))
    }

    @Test
    fun flattensAPhotographedPageIntoARectangle() {
        val photograph = Distortions.photographAtAnAngle(clean)
        val outline = assertNotNull(DocumentEdgeDetector.detect(photograph))
        val corrected = preprocessor.correctPerspective(photograph, outline)

        // The corrected page should have roughly the aspect ratio of the original
        // receipt rather than the skewed one.
        val originalRatio = clean.height.toDouble() / clean.width
        val correctedRatio = corrected.height.toDouble() / corrected.width
        assertTrue(
            abs(correctedRatio - originalRatio) < 0.25,
            "expected a ratio near $originalRatio, got $correctedRatio",
        )
    }

    @Test
    fun reportsEveryStepItApplied() {
        val result = preprocessor.prepare(clean)
        assertTrue(PreprocessStep.GREYSCALE in result.steps)
        assertTrue(PreprocessStep.CONTRAST in result.steps)
        assertTrue(PreprocessStep.THRESHOLD in result.steps)
        assertTrue(PreprocessStep.DENOISE in result.steps)
    }

    @Test
    fun correctsPerspectiveWhenItFindsAPageAndSaysSo() {
        val photograph = Distortions.photographAtAnAngle(clean)
        val result = preprocessor.prepare(photograph)
        assertTrue(result.documentFound)
        assertTrue(PreprocessStep.PERSPECTIVE_CORRECTION in result.steps)

        // The desk around the paper is gone, so the output has the shape of the
        // receipt rather than the shape of the photograph.
        val receiptRatio = clean.height.toDouble() / clean.width
        val photographRatio = photograph.height.toDouble() / photograph.width
        val resultRatio = result.image.height.toDouble() / result.image.width
        assertTrue(
            abs(resultRatio - receiptRatio) < abs(resultRatio - photographRatio),
            "expected the receipt's shape ($receiptRatio), not the photo's ($photographRatio), got $resultRatio",
        )
    }

    @Test
    fun honoursACropThePersonAdjustedByHand() {
        val photograph = Distortions.photographAtAnAngle(clean)
        val manual = Quadrilateral(
            Point2(50.0, 50.0),
            Point2(photograph.width - 50.0, 60.0),
            Point2(photograph.width - 60.0, photograph.height - 50.0),
            Point2(40.0, photograph.height - 60.0),
        )
        val result = preprocessor.prepare(photograph, PreprocessOptions(manualOutline = manual))
        assertEquals(manual, result.documentOutline)
    }

    @Test
    fun enlargesSmallImagesSoSmallPrintSurvives() {
        val small = ReceiptImageRenderer.render(generator.spec(layout = ReceiptLayout.COMPACT))
        val result = preprocessor.prepare(small, PreprocessOptions(targetWidth = 2_400, correctPerspective = false))
        assertTrue(PreprocessStep.UPSCALE in result.steps)
        assertEquals(2_400, result.image.width)
    }

    @Test
    fun doesNothingWhenAskedToDoNothing() {
        val result = preprocessor.prepare(clean, PreprocessOptions.NONE)
        assertEquals(listOf(PreprocessStep.GREYSCALE), result.steps)
        assertEquals(clean.width, result.image.width)
    }

    @Test
    fun producesAnImageWithInkAndPaperRatherThanMush() {
        val faded = ReceiptImageRenderer.render(generator.spec(), CaptureConditions.FADED)
        val result = preprocessor.prepare(faded, PreprocessOptions(correctPerspective = false))

        var dark = 0
        var light = 0
        for (y in 0 until result.image.height step 4) {
            for (x in 0 until result.image.width step 4) {
                if ((result.image.getRGB(x, y) and 0xFF) < 128) dark++ else light++
            }
        }
        assertTrue(dark > 0, "thresholding should leave ink behind")
        assertTrue(light > dark, "a receipt is mostly paper")
    }

    @Test
    fun neverModifiesTheImageItWasGiven() {
        val before = clean.getRGB(0, 0, clean.width, clean.height, null, 0, clean.width)
        preprocessor.prepare(clean)
        val after = clean.getRGB(0, 0, clean.width, clean.height, null, 0, clean.width)
        assertTrue(before.contentEquals(after), "the original image must not be touched")
    }
}
