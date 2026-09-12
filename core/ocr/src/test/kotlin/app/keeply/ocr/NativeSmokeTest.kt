package app.keeply.ocr

import org.bytedeco.tesseract.TessBaseAPI
import kotlin.test.Test
import kotlin.test.assertNotNull

class NativeSmokeTest {
    @Test
    fun tesseractLoads() {
        val api = TessBaseAPI()
        assertNotNull(api)
        println("Tesseract native OK: " + TessBaseAPI.Version().string)
        api.close()
    }
}
