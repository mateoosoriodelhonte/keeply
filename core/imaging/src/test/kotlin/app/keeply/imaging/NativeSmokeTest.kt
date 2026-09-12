package app.keeply.imaging

import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeSmokeTest {
    @Test
    fun opencvLoads() {
        val m = Mat(3, 4, opencv_core.CV_8UC1)
        assertEquals(3, m.rows())
        assertEquals(4, m.cols())
        println("OpenCV native OK: " + opencv_core.getVersionString().string)
        m.close()
    }
}
