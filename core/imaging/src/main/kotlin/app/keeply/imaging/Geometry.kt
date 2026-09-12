package app.keeply.imaging

import kotlin.math.hypot

/** A point in image space, in pixels from the top-left. */
public data class Point2(val x: Double, val y: Double) {
    public fun distanceTo(other: Point2): Double = hypot(x - other.x, y - other.y)

    public fun scaled(factor: Double): Point2 = Point2(x * factor, y * factor)
}

/**
 * The four corners of a sheet of paper in a photograph, in reading order.
 *
 * The crop editor shows these as draggable handles, so this is also the shape a
 * person's correction comes back in.
 */
public data class Quadrilateral(val topLeft: Point2, val topRight: Point2, val bottomRight: Point2, val bottomLeft: Point2) {
    public val points: List<Point2> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Shoelace area, used to reject a "document" that is really a small bright patch. */
    public val area: Double
        get() {
            val p = points
            var sum = 0.0
            for (i in p.indices) {
                val next = p[(i + 1) % p.size]
                sum += p[i].x * next.y - next.x * p[i].y
            }
            return kotlin.math.abs(sum) / 2.0
        }

    /** Output size for a corrected image: the longer of each pair of opposite edges. */
    public val correctedWidth: Int
        get() = maxOf(topLeft.distanceTo(topRight), bottomLeft.distanceTo(bottomRight)).toInt().coerceAtLeast(1)

    public val correctedHeight: Int
        get() = maxOf(topLeft.distanceTo(bottomLeft), topRight.distanceTo(bottomRight)).toInt().coerceAtLeast(1)

    public fun scaled(factor: Double): Quadrilateral = Quadrilateral(
        topLeft.scaled(factor),
        topRight.scaled(factor),
        bottomRight.scaled(factor),
        bottomLeft.scaled(factor),
    )

    public companion object {
        /**
         * Puts four corners found in an arbitrary order into reading order.
         *
         * Sorting by the sum and difference of the coordinates is the standard trick:
         * the top-left has the smallest sum, the bottom-right the largest, and the
         * other two are separated by x minus y.
         */
        public fun fromUnordered(points: List<Point2>): Quadrilateral {
            require(points.size == 4) { "A document outline needs exactly four corners" }
            val bySum = points.sortedBy { it.x + it.y }
            val byDifference = points.sortedBy { it.x - it.y }
            return Quadrilateral(
                topLeft = bySum.first(),
                bottomRight = bySum.last(),
                bottomLeft = byDifference.first(),
                topRight = byDifference.last(),
            )
        }

        /** The whole image, used when no document outline could be found. */
        public fun wholeImage(width: Int, height: Int): Quadrilateral = Quadrilateral(
            topLeft = Point2(0.0, 0.0),
            topRight = Point2(width.toDouble(), 0.0),
            bottomRight = Point2(width.toDouble(), height.toDouble()),
            bottomLeft = Point2(0.0, height.toDouble()),
        )
    }
}
