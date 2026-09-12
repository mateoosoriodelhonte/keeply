package app.keeply.imaging

import org.bytedeco.javacpp.indexer.IntIndexer
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Size
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

/**
 * Finds the sheet of paper in a photograph.
 *
 * Looks for the largest convex four-sided outline that covers a plausible share of
 * the frame. If nothing convincing is there, it says so: cropping to the wrong
 * rectangle throws away half a receipt, which is far worse than leaving a photo of
 * a desk alone and letting the person crop it themselves.
 */
public object DocumentEdgeDetector {
    private val log = LoggerFactory.getLogger(DocumentEdgeDetector::class.java)

    /** Detection runs on a downscaled copy: edges are just as findable and it is much faster. */
    private const val WORKING_WIDTH = 900.0

    /** Below this share of the frame, a quadrilateral is a sign or a tile, not the receipt. */
    private const val MIN_AREA_FRACTION = 0.18

    /** Above this, the "document" is the whole photo and cropping would achieve nothing. */
    private const val MAX_AREA_FRACTION = 0.995

    private const val APPROX_EPSILON_FRACTION = 0.02
    private const val CANDIDATES_TO_TRY = 8

    public fun detect(image: BufferedImage): Quadrilateral? = Mats.withMat(image) { source ->
        val scale = if (image.width > WORKING_WIDTH) WORKING_WIDTH / image.width else 1.0
        val working = Mat()
        val grey = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val contours = MatVector()

        try {
            if (scale < 1.0) {
                opencv_imgproc.resize(source, working, Size((image.width * scale).toInt(), (image.height * scale).toInt()))
            } else {
                source.copyTo(working)
            }

            opencv_imgproc.cvtColor(working, grey, opencv_imgproc.COLOR_BGR2GRAY)
            opencv_imgproc.GaussianBlur(grey, blurred, Size(5, 5), 0.0)
            opencv_imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)
            // Paper edges break up under uneven light; closing the gaps turns a
            // dashed outline back into a contour that can actually be traced.
            opencv_imgproc.dilate(edges, dilated, Mat())

            opencv_imgproc.findContours(
                dilated,
                contours,
                opencv_imgproc.RETR_LIST,
                opencv_imgproc.CHAIN_APPROX_SIMPLE,
            )

            val frameArea = working.rows().toDouble() * working.cols().toDouble()
            val best = bestQuadrilateral(contours, frameArea) ?: return@withMat null
            val restored = if (scale < 1.0) best.scaled(1.0 / scale) else best
            log.debug("Found a document outline covering {}% of the frame", (best.area / frameArea * 100).toInt())
            restored
        } finally {
            listOf(working, grey, blurred, edges, dilated).closeAll()
            contours.close()
        }
    }

    private fun bestQuadrilateral(contours: MatVector, frameArea: Double): Quadrilateral? {
        val ranked = (0 until contours.size().toInt())
            .map { index -> contours.get(index.toLong()) }
            .sortedByDescending { opencv_imgproc.contourArea(it) }
            .take(CANDIDATES_TO_TRY)

        ranked.forEach { contour ->
            val approximation = Mat()
            try {
                val perimeter = opencv_imgproc.arcLength(contour, true)
                opencv_imgproc.approxPolyDP(contour, approximation, APPROX_EPSILON_FRACTION * perimeter, true)
                if (approximation.rows() != 4) return@forEach
                if (!opencv_imgproc.isContourConvex(approximation)) return@forEach

                val quad = Quadrilateral.fromUnordered(cornersOf(approximation))
                val fraction = quad.area / frameArea
                if (fraction in MIN_AREA_FRACTION..MAX_AREA_FRACTION) return quad
            } finally {
                approximation.close()
            }
        }
        return null
    }

    private fun cornersOf(approximation: Mat): List<Point2> = approximation.createIndexer<IntIndexer>().use { indexer ->
        (0 until 4).map { row ->
            Point2(indexer.get(row.toLong(), 0L, 0L).toDouble(), indexer.get(row.toLong(), 0L, 1L).toDouble())
        }
    }

    private const val CANNY_LOW = 60.0
    private const val CANNY_HIGH = 180.0
}
