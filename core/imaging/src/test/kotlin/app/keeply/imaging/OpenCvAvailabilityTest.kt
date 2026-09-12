package app.keeply.imaging

import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeply bundles its own OpenCV binaries so nobody has to install anything. If the
 * native library stops loading, image preprocessing silently degrades, so this is
 * checked on every build and on every CI platform.
 */
class OpenCvAvailabilityTest {
    @Test
    fun nativeLibraryLoadsAndAllocates() {
        Mat(3, 4, opencv_core.CV_8UC1).use { mat ->
            assertEquals(3, mat.rows())
            assertEquals(4, mat.cols())
        }
    }

    @Test
    fun reportsAnExpectedVersion() {
        val version = opencv_core.getVersionString().string
        assertTrue(version.startsWith("4."), "Unexpected OpenCV version: $version")
    }
}
