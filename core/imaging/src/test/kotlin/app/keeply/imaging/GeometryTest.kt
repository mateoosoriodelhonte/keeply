package app.keeply.imaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeometryTest {
    @Test
    fun putsCornersIntoReadingOrderWhateverOrderTheyArrivedIn() {
        val corners = listOf(
            Point2(100.0, 300.0), // bottom left
            Point2(110.0, 20.0), // top left
            Point2(400.0, 310.0), // bottom right
            Point2(390.0, 30.0), // top right
        )
        val quad = Quadrilateral.fromUnordered(corners.shuffled())
        assertEquals(Point2(110.0, 20.0), quad.topLeft)
        assertEquals(Point2(390.0, 30.0), quad.topRight)
        assertEquals(Point2(400.0, 310.0), quad.bottomRight)
        assertEquals(Point2(100.0, 300.0), quad.bottomLeft)
    }

    @Test
    fun measuresAreaSoASmallBrightPatchCanBeRejected() {
        val square = Quadrilateral(
            Point2(0.0, 0.0),
            Point2(10.0, 0.0),
            Point2(10.0, 10.0),
            Point2(0.0, 10.0),
        )
        assertEquals(100.0, square.area)
    }

    @Test
    fun sizesTheCorrectedImageFromTheLongerEdges() {
        // A page photographed at an angle has one short edge; using it would squash
        // the text.
        val skewed = Quadrilateral(
            Point2(0.0, 0.0),
            Point2(100.0, 10.0),
            Point2(120.0, 210.0),
            Point2(5.0, 190.0),
        )
        assertTrue(skewed.correctedWidth >= 100)
        assertTrue(skewed.correctedHeight >= 190)
    }

    @Test
    fun scalesBackToTheOriginalResolution() {
        // Detection runs on a downscaled copy, so the outline has to scale back up.
        val small = Quadrilateral(
            Point2(10.0, 10.0),
            Point2(50.0, 10.0),
            Point2(50.0, 90.0),
            Point2(10.0, 90.0),
        )
        val full = small.scaled(4.0)
        assertEquals(Point2(40.0, 40.0), full.topLeft)
        assertEquals(Point2(200.0, 360.0), full.bottomRight)
    }

    @Test
    fun needsExactlyFourCorners() {
        assertFailsWith<IllegalArgumentException> {
            Quadrilateral.fromUnordered(listOf(Point2(0.0, 0.0), Point2(1.0, 1.0)))
        }
    }
}
