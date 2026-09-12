package app.keeply.imaging

import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Makes the small images the library grid shows.
 *
 * Thumbnails are derived data: they live in their own folder, the storage screen
 * offers to delete them, and Keeply rebuilds them on demand.
 */
public object Thumbnailer {

    public const val CARD_WIDTH: Int = 420
    public const val GRID_WIDTH: Int = 220

    public fun thumbnail(image: BufferedImage, targetWidth: Int = CARD_WIDTH): BufferedImage {
        if (image.width <= targetWidth) return image
        return Mats.withMat(image) { source ->
            val scale = targetWidth.toDouble() / image.width
            val resized = Mat()
            try {
                opencv_imgproc.resize(
                    source,
                    resized,
                    Size(targetWidth, (image.height * scale).toInt().coerceAtLeast(1)),
                    0.0,
                    0.0,
                    // Correct downscaling filter: averages the pixels being merged
                    // instead of sampling one of them and aliasing the text.
                    opencv_imgproc.INTER_AREA,
                )
                Mats.toImage(resized)
            } finally {
                resized.close()
            }
        }
    }

    public fun toJpeg(image: BufferedImage): ByteArray {
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB || image.type == BufferedImage.TYPE_3BYTE_BGR) {
            image
        } else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_3BYTE_BGR).also { converted ->
                converted.createGraphics().apply {
                    drawImage(image, 0, 0, null)
                    dispose()
                }
            }
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(rgb, "jpg", out)
            out.toByteArray()
        }
    }
}
