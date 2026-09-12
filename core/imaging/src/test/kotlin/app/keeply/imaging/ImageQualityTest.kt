package app.keeply.imaging

import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageQualityTest {
    private val generator = ReceiptGenerator(seed = 55)
    private val spec = generator.spec()

    @Test
    fun sayNothingAboutAGoodPhoto() {
        val assessment = ImageQuality.assess(ReceiptImageRenderer.render(spec))
        assertNull(assessment.advice(), "a clean render should not be criticised")
        assertTrue(!assessment.isBlurry)
    }

    @Test
    fun noticesABlurryPhotoBeforeTheUserWaitsForOcr() {
        val blurry = ReceiptImageRenderer.render(spec, CaptureConditions.BLURRY)
        val assessment = ImageQuality.assess(blurry)
        assertTrue(assessment.isBlurry, "sharpness was ${assessment.sharpness}")
        assertNotNull(assessment.advice())
        assertTrue(assessment.advice()!!.contains("blurry"))
    }

    @Test
    fun noticesAWashedOutPhoto() {
        val faded = ReceiptImageRenderer.render(spec, CaptureConditions.FADED)
        val assessment = ImageQuality.assess(faded)
        assertTrue(
            assessment.isTooFlat || assessment.isBlurry,
            "faded print should be flagged: contrast ${assessment.contrast}, sharpness ${assessment.sharpness}",
        )
    }

    @Test
    fun givesAdviceRatherThanRefusing() {
        // Keeply always tries. The advice exists to set expectations, not to block.
        val terrible = ReceiptImageRenderer.render(
            spec,
            CaptureConditions(blurRadius = 6, inkFade = 0.7, noise = 0.2),
        )
        val assessment = ImageQuality.assess(terrible)
        assertNotNull(assessment.advice())
        assertTrue(assessment.advice()!!.endsWith("."), "advice should read like a sentence")
    }
}
