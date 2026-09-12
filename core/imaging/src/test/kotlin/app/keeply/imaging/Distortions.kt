package app.keeply.imaging

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import java.awt.image.BufferedImage

/**
 * Bends a clean receipt the way a phone camera does, so the preprocessor can be
 * tested against the problem it exists to solve.
 */
internal object Distortions {

    /**
     * Photographs the receipt from an angle: the sheet is placed on a dark surface
     * and its corners are pulled in as perspective would.
     */
    fun photographAtAnAngle(image: BufferedImage, strength: Double = 0.18): BufferedImage {
        val margin = (image.width * 0.18).toInt()
        val canvasWidth = image.width + margin * 2
        val canvasHeight = image.height + margin * 2

        return Mats.withMat(image) { source ->
            // Pre-filled with the colour of the surface the photo is taken on.
            // warpPerspective with BORDER_TRANSPARENT leaves these pixels alone
            // wherever the paper does not cover them.
            val canvas = Mat(canvasHeight, canvasWidth, opencv_core.CV_8UC3, Scalar(52.0, 56.0, 62.0, 0.0))
            val from = points(
                listOf(
                    Point2(0.0, 0.0),
                    Point2(image.width - 1.0, 0.0),
                    Point2(image.width - 1.0, image.height - 1.0),
                    Point2(0.0, image.height - 1.0),
                ),
            )
            val inset = image.width * strength
            val to = points(
                listOf(
                    Point2(margin + inset, margin.toDouble()),
                    Point2(canvasWidth - margin - 1.0, margin + inset * 0.35),
                    Point2(canvasWidth - margin - inset * 0.6, canvasHeight - margin - 1.0),
                    Point2(margin.toDouble(), canvasHeight - margin - inset * 0.5),
                ),
            )
            val transform = opencv_imgproc.getPerspectiveTransform(from, to)
            try {
                opencv_imgproc.warpPerspective(
                    source,
                    canvas,
                    transform,
                    Size(canvasWidth, canvasHeight),
                    opencv_imgproc.INTER_LINEAR,
                    opencv_core.BORDER_TRANSPARENT,
                    Scalar.all(0.0),
                )
                Mats.toImage(canvas)
            } finally {
                listOf(canvas, from, to, transform).closeAll()
            }
        }
    }

    private fun points(list: List<Point2>): Mat {
        val mat = Mat(list.size, 1, opencv_core.CV_32FC2)
        mat.createIndexer<FloatIndexer>().use { indexer ->
            list.forEachIndexed { index, point ->
                indexer.put(index.toLong(), 0L, 0L, point.x.toFloat())
                indexer.put(index.toLong(), 0L, 1L, point.y.toFloat())
            }
        }
        return mat
    }
}
