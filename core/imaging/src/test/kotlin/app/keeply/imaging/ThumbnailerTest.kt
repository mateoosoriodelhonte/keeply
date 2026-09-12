package app.keeply.imaging

import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThumbnailerTest {
    private val image = ReceiptImageRenderer.render(ReceiptGenerator(seed = 8).spec())

    @Test
    fun scalesToTheRequestedWidthAndKeepsTheShape() {
        val thumbnail = Thumbnailer.thumbnail(image, Thumbnailer.GRID_WIDTH)
        assertEquals(Thumbnailer.GRID_WIDTH, thumbnail.width)
        val originalRatio = image.height.toDouble() / image.width
        val thumbnailRatio = thumbnail.height.toDouble() / thumbnail.width
        assertTrue(kotlin.math.abs(originalRatio - thumbnailRatio) < 0.02)
    }

    @Test
    fun doesNotEnlargeSomethingAlreadySmall() {
        val small = Thumbnailer.thumbnail(image, Thumbnailer.GRID_WIDTH)
        assertSame(small, Thumbnailer.thumbnail(small, Thumbnailer.CARD_WIDTH))
    }

    @Test
    fun writesARealJpeg() {
        val bytes = Thumbnailer.toJpeg(Thumbnailer.thumbnail(image))
        assertEquals(listOf(0xFF.toByte(), 0xD8.toByte()), bytes.take(2))
        assertTrue(bytes.size < 200_000, "a thumbnail should be small, was ${bytes.size} bytes")
    }
}
