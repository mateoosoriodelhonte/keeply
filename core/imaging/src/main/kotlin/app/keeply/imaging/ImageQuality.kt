package app.keeply.imaging

import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import java.awt.image.BufferedImage

/** What Keeply can tell about a photograph before trying to read it. */
public data class ImageAssessment(
    /** Variance of the Laplacian. Higher is sharper; under about 60 is visibly soft. */
    val sharpness: Double,
    /** Mean brightness, 0 to 255. */
    val brightness: Double,
    /** Standard deviation of brightness. Low means a flat, washed-out image. */
    val contrast: Double,
    val width: Int,
    val height: Int,
) {
    public val isBlurry: Boolean get() = sharpness < BLURRY_BELOW
    public val isTooDark: Boolean get() = brightness < DARK_BELOW
    public val isTooFlat: Boolean get() = contrast < FLAT_BELOW

    /**
     * A till roll is legitimately narrow and tall, so this asks whether the page is
     * wide enough for small print to survive rather than looking at the shorter side.
     */
    public val isTooSmall: Boolean get() = width < NARROW_BELOW || height < SHORT_BELOW

    /**
     * A sentence to show the person when OCR is likely to disappoint, or null when
     * the photo is fine. Advice, never a refusal: Keeply will still try.
     */
    public fun advice(): String? = when {
        isTooSmall -> "That image is quite small, so Keeply may not read it well. A closer photo would help."
        isBlurry -> "That photo looks blurry. Keeply will still try, but a steadier shot reads much better."
        isTooDark -> "That photo is quite dark. More light on the receipt would help Keeply read it."
        isTooFlat -> "That photo is washed out. Keeply will try to correct it."
        else -> null
    }

    private companion object {
        const val BLURRY_BELOW = 60.0
        const val DARK_BELOW = 70.0
        const val FLAT_BELOW = 25.0
        const val NARROW_BELOW = 500
        const val SHORT_BELOW = 300
    }
}

/**
 * Measures how usable a photograph is.
 *
 * Telling someone their photo is blurry before they wait for OCR is worth more
 * than a better algorithm afterwards.
 */
public object ImageQuality {

    public fun assess(image: BufferedImage): ImageAssessment = Mats.withMat(image) { source ->
        val grey = Mat()
        val laplacian = Mat()
        val mean = Mat()
        val deviation = Mat()
        val greyMean = Mat()
        val greyDeviation = Mat()
        try {
            opencv_imgproc.cvtColor(source, grey, opencv_imgproc.COLOR_BGR2GRAY)
            opencv_imgproc.Laplacian(grey, laplacian, opencv_core.CV_64F)
            opencv_core.meanStdDev(laplacian, mean, deviation)
            opencv_core.meanStdDev(grey, greyMean, greyDeviation)

            val sigma = deviation.createIndexer<org.bytedeco.javacpp.indexer.DoubleIndexer>().use { it.get(0, 0) }
            val brightness = greyMean.createIndexer<org.bytedeco.javacpp.indexer.DoubleIndexer>().use { it.get(0, 0) }
            val contrast = greyDeviation.createIndexer<org.bytedeco.javacpp.indexer.DoubleIndexer>().use { it.get(0, 0) }

            ImageAssessment(
                sharpness = sigma * sigma,
                brightness = brightness,
                contrast = contrast,
                width = image.width,
                height = image.height,
            )
        } finally {
            listOf(grey, laplacian, mean, deviation, greyMean, greyDeviation).closeAll()
        }
    }
}
