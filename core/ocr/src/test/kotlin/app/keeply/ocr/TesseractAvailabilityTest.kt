package app.keeply.ocr

import org.bytedeco.tesseract.TessBaseAPI
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeply performs OCR locally with a Tesseract binary it ships itself. Nothing is
 * uploaded, and nothing needs installing. This test fails loudly if that stops
 * being true on any platform CI builds for.
 */
class TesseractAvailabilityTest {
    @Test
    fun nativeLibraryLoads() {
        TessBaseAPI().use { api ->
            assertTrue(api.address() != 0L, "Tesseract API handle was not allocated")
        }
    }

    @Test
    fun reportsAnExpectedVersion() {
        val version = TessBaseAPI.Version().string
        assertTrue(version.startsWith("5."), "Unexpected Tesseract version: $version")
    }
}
