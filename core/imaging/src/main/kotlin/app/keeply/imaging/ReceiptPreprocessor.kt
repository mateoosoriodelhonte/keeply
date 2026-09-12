package app.keeply.imaging

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

/** A step Keeply applied to a photograph, shown in the OCR debug view. */
public enum class PreprocessStep {
    PERSPECTIVE_CORRECTION,
    GREYSCALE,
    UPSCALE,
    CONTRAST,
    DENOISE,
    THRESHOLD,
}

/** What to do to a photograph before reading it. */
public data class PreprocessOptions(
    val correctPerspective: Boolean = true,
    val enhanceContrast: Boolean = true,
    val denoise: Boolean = true,
    val threshold: Boolean = true,
    /**
     * Tesseract wants characters around 30 pixels tall. Small photos are enlarged
     * to roughly this width before reading, which measurably helps on till receipts.
     */
    val targetWidth: Int = 1_800,
    /** A crop the person adjusted by hand. Overrides automatic detection. */
    val manualOutline: Quadrilateral? = null,
) {
    public companion object {
        public val DEFAULT: PreprocessOptions = PreprocessOptions()

        /** Leaves the image alone. Used to compare against, and for clean scans. */
        public val NONE: PreprocessOptions = PreprocessOptions(
            correctPerspective = false,
            enhanceContrast = false,
            denoise = false,
            threshold = false,
            targetWidth = 0,
        )
    }
}

/** The cleaned-up image, plus an account of what was done to it and why. */
public data class PreprocessResult(
    val image: BufferedImage,
    val steps: List<PreprocessStep>,
    val documentOutline: Quadrilateral?,
    val assessment: ImageAssessment,
) {
    public val documentFound: Boolean get() = documentOutline != null
}

/**
 * Turns a photograph of a receipt into something worth running OCR on.
 *
 * Photographs of receipts are skewed, creased and badly lit, and OCR on the raw
 * image performs poorly. Each step here earns its place; none of it is applied for
 * the sake of using OpenCV.
 *
 * The original image is never modified. This produces a new image, which Keeply
 * stores separately as derived data that can be thrown away and rebuilt.
 */
public class ReceiptPreprocessor {
    private val log = LoggerFactory.getLogger(ReceiptPreprocessor::class.java)

    public fun prepare(image: BufferedImage, options: PreprocessOptions = PreprocessOptions.DEFAULT): PreprocessResult {
        val assessment = ImageQuality.assess(image)
        val steps = mutableListOf<PreprocessStep>()

        val outline = when {
            !options.correctPerspective -> null
            options.manualOutline != null -> options.manualOutline
            else -> DocumentEdgeDetector.detect(image)
        }

        var current = image
        if (outline != null) {
            current = correctPerspective(current, outline)
            steps += PreprocessStep.PERSPECTIVE_CORRECTION
        } else if (options.correctPerspective) {
            // No convincing outline. Cropping to a guess would throw away half a
            // receipt, so the whole frame is kept and the person can crop it.
            log.debug("No document outline found; keeping the full frame")
        }

        val result = Mats.withMat(current) { source ->
            var working = Mat()
            source.copyTo(working)
            val intermediates = mutableListOf(working)

            fun replace(next: Mat) {
                working = next
                intermediates += next
            }

            try {
                val grey = Mat()
                opencv_imgproc.cvtColor(working, grey, opencv_imgproc.COLOR_BGR2GRAY)
                replace(grey)
                steps += PreprocessStep.GREYSCALE

                if (options.targetWidth > 0 && grey.cols() < options.targetWidth) {
                    val factor = options.targetWidth.toDouble() / grey.cols()
                    val enlarged = Mat()
                    opencv_imgproc.resize(
                        working,
                        enlarged,
                        Size(options.targetWidth, (grey.rows() * factor).toInt()),
                        0.0,
                        0.0,
                        opencv_imgproc.INTER_CUBIC,
                    )
                    replace(enlarged)
                    steps += PreprocessStep.UPSCALE
                }

                if (options.denoise) {
                    val smoothed = Mat()
                    // Preserves the edges of characters while flattening sensor noise,
                    // which a plain blur would not.
                    opencv_imgproc.bilateralFilter(working, smoothed, DENOISE_DIAMETER, DENOISE_SIGMA, DENOISE_SIGMA)
                    replace(smoothed)
                    steps += PreprocessStep.DENOISE
                }

                if (options.enhanceContrast) {
                    val equalised = Mat()
                    // Local rather than global, because a photographed receipt is
                    // usually bright at one end and shadowed at the other.
                    val clahe = opencv_imgproc.createCLAHE(CLAHE_CLIP, Size(CLAHE_TILE, CLAHE_TILE))
                    clahe.apply(working, equalised)
                    clahe.close()
                    replace(equalised)
                    steps += PreprocessStep.CONTRAST
                }

                if (options.threshold) {
                    val binary = Mat()
                    opencv_imgproc.adaptiveThreshold(
                        working,
                        binary,
                        255.0,
                        opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        opencv_imgproc.THRESH_BINARY,
                        THRESHOLD_BLOCK,
                        THRESHOLD_CONSTANT,
                    )
                    replace(binary)
                    steps += PreprocessStep.THRESHOLD
                }

                Mats.toImage(working)
            } finally {
                intermediates.closeAll()
            }
        }

        return PreprocessResult(result, steps.toList(), outline, assessment)
    }

    /** Flattens a photographed page into a rectangle. */
    public fun correctPerspective(image: BufferedImage, outline: Quadrilateral): BufferedImage = Mats.withMat(image) { source ->
        val width = outline.correctedWidth
        val height = outline.correctedHeight
        val from = pointsMat(outline.points)
        val to = pointsMat(
            listOf(
                Point2(0.0, 0.0),
                Point2(width - 1.0, 0.0),
                Point2(width - 1.0, height - 1.0),
                Point2(0.0, height - 1.0),
            ),
        )
        val transform = opencv_imgproc.getPerspectiveTransform(from, to)
        val corrected = Mat()
        try {
            opencv_imgproc.warpPerspective(source, corrected, transform, Size(width, height))
            Mats.toImage(corrected)
        } finally {
            listOf(from, to, transform, corrected).closeAll()
        }
    }

    private fun pointsMat(points: List<Point2>): Mat {
        val mat = Mat(points.size, 1, opencv_core.CV_32FC2)
        mat.createIndexer<FloatIndexer>().use { indexer ->
            points.forEachIndexed { index, point ->
                indexer.put(index.toLong(), 0L, 0L, point.x.toFloat())
                indexer.put(index.toLong(), 0L, 1L, point.y.toFloat())
            }
        }
        return mat
    }

    private companion object {
        const val DENOISE_DIAMETER = 7
        const val DENOISE_SIGMA = 45.0
        const val CLAHE_CLIP = 2.5
        const val CLAHE_TILE = 8
        const val THRESHOLD_BLOCK = 31
        const val THRESHOLD_CONSTANT = 12.0
    }
}
