package app.keeply.fixtures

import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Maps a rectangular image onto an arbitrary quadrilateral, the way a camera held
 * above a table at an angle does.
 *
 * Java2D can rotate and shear but cannot do perspective, and a receipt photographed
 * from anywhere but directly overhead has converging edges. Testing the
 * preprocessor against rotation alone would have flattered it, so the distortion is
 * produced properly here.
 *
 * Implemented with plain arithmetic rather than OpenCV, so the fixtures stay usable
 * by any module without dragging a native dependency along.
 */
public object PerspectiveWarp {

    /**
     * Draws [source] into a canvas of [canvasWidth] by [canvasHeight], with its
     * corners landing on [destination] in top-left, top-right, bottom-right,
     * bottom-left order. Everything outside the paper is filled with [background].
     */
    public fun apply(
        source: BufferedImage,
        destination: List<DoubleArray>,
        canvasWidth: Int,
        canvasHeight: Int,
        background: Color,
    ): BufferedImage {
        require(destination.size == 4) { "A perspective warp needs four destination corners" }

        val forward = homography(
            from = listOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(source.width - 1.0, 0.0),
                doubleArrayOf(source.width - 1.0, source.height - 1.0),
                doubleArrayOf(0.0, source.height - 1.0),
            ),
            to = destination,
        )
        val inverse = invert(forward)

        val result = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB)
        val backgroundRgb = background.rgb
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                val w = inverse[6] * x + inverse[7] * y + inverse[8]
                if (w == 0.0) {
                    result.setRGB(x, y, backgroundRgb)
                    continue
                }
                val sourceX = (inverse[0] * x + inverse[1] * y + inverse[2]) / w
                val sourceY = (inverse[3] * x + inverse[4] * y + inverse[5]) / w
                result.setRGB(
                    x,
                    y,
                    if (sourceX < 0 || sourceY < 0 || sourceX > source.width - 1 || sourceY > source.height - 1) {
                        backgroundRgb
                    } else {
                        sample(source, sourceX, sourceY)
                    },
                )
            }
        }
        return result
    }

    /** Bilinear, so the paper does not come out with jagged character edges. */
    private fun sample(image: BufferedImage, x: Double, y: Double): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(image.width - 1)
        val y1 = (y0 + 1).coerceAtMost(image.height - 1)
        val fx = x - x0
        val fy = y - y0

        val topLeft = image.getRGB(x0, y0)
        val topRight = image.getRGB(x1, y0)
        val bottomLeft = image.getRGB(x0, y1)
        val bottomRight = image.getRGB(x1, y1)

        var rgb = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val top = (topLeft shr shift and 0xFF) * (1 - fx) + (topRight shr shift and 0xFF) * fx
            val bottom = (bottomLeft shr shift and 0xFF) * (1 - fx) + (bottomRight shr shift and 0xFF) * fx
            val value = (top * (1 - fy) + bottom * fy).toInt().coerceIn(0, 255)
            rgb = rgb or (value shl shift)
        }
        return rgb
    }

    /**
     * Solves for the 3x3 projective transform taking four points to four points.
     *
     * Eight unknowns, eight equations, Gaussian elimination with partial pivoting.
     */
    private fun homography(from: List<DoubleArray>, to: List<DoubleArray>): DoubleArray {
        val matrix = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]
            val (u, v) = to[i]
            matrix[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            matrix[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }

        for (column in 0 until 8) {
            var pivot = column
            for (row in column + 1 until 8) {
                if (kotlin.math.abs(matrix[row][column]) > kotlin.math.abs(matrix[pivot][column])) pivot = row
            }
            val swap = matrix[column]
            matrix[column] = matrix[pivot]
            matrix[pivot] = swap

            val divisor = matrix[column][column]
            require(divisor != 0.0) { "Degenerate quadrilateral: the four corners are not independent" }
            for (k in column until 9) matrix[column][k] /= divisor

            for (row in 0 until 8) {
                if (row == column) continue
                val factor = matrix[row][column]
                if (factor == 0.0) continue
                for (k in column until 9) matrix[row][k] -= factor * matrix[column][k]
            }
        }

        return DoubleArray(9) { index -> if (index == 8) 1.0 else matrix[index][8] }
    }

    private fun invert(h: DoubleArray): DoubleArray {
        val a = h[4] * h[8] - h[5] * h[7]
        val b = h[5] * h[6] - h[3] * h[8]
        val c = h[3] * h[7] - h[4] * h[6]
        val determinant = h[0] * a + h[1] * b + h[2] * c
        require(determinant != 0.0) { "Transform is not invertible" }

        return doubleArrayOf(
            a / determinant,
            (h[2] * h[7] - h[1] * h[8]) / determinant,
            (h[1] * h[5] - h[2] * h[4]) / determinant,
            b / determinant,
            (h[0] * h[8] - h[2] * h[6]) / determinant,
            (h[2] * h[3] - h[0] * h[5]) / determinant,
            c / determinant,
            (h[1] * h[6] - h[0] * h[7]) / determinant,
            (h[0] * h[4] - h[1] * h[3]) / determinant,
        )
    }

    private operator fun DoubleArray.component1(): Double = this[0]
    private operator fun DoubleArray.component2(): Double = this[1]
}
