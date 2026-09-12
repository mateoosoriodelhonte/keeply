package app.keeply.fixtures

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * How badly the receipt was captured.
 *
 * Real imports are photographs taken at an angle on a kitchen table under a warm
 * bulb, not flatbed scans. These knobs reproduce the failures that actually matter:
 * a skewed page, a faded thermal print, a slightly out-of-focus phone camera.
 */
public data class CaptureConditions(
    val rotationDegrees: Double = 0.0,
    /** 1.0 leaves contrast alone; 0.35 is a receipt that has been in a wallet for a month. */
    val contrast: Double = 1.0,
    /** Added to every channel. Positive washes the image out. */
    val brightness: Int = 0,
    val blurRadius: Int = 0,
    /**
     * How much the print has faded, 0.0 to 1.0. Thermal paper loses its ink toward
     * white rather than washing the whole image toward grey, so this lightens dark
     * pixels and leaves the paper where it is.
     */
    val inkFade: Double = 0.0,
    /** Fraction of pixels given sensor noise, 0.0 to 1.0. */
    val noise: Double = 0.0,
    /** Pixels of desk visible around the paper, as when photographing a receipt. */
    val surroundMargin: Int = 0,
    /**
     * How far from directly overhead the camera was, 0.0 to about 0.35.
     *
     * A receipt photographed from an angle has converging edges, which rotation
     * alone does not reproduce. This is the distortion the preprocessor's
     * perspective correction exists to undo.
     */
    val perspective: Double = 0.0,
    val surroundColour: Color = Color(64, 62, 58),
    val seed: Long = 1L,
) {
    public companion object {
        /** A flatbed scan: what a receipt looks like at its best. */
        public val CLEAN: CaptureConditions = CaptureConditions()

        /** A phone photo taken over a desk: askew, at an angle, on a visible surface. */
        public val PHOTOGRAPHED: CaptureConditions = CaptureConditions(
            rotationDegrees = 3.0,
            surroundMargin = 90,
            perspective = 0.16,
            blurRadius = 1,
            noise = 0.01,
        )

        /** Straight on, but skewed on the desk. */
        public val SKEWED: CaptureConditions =
            CaptureConditions(rotationDegrees = 6.0, surroundMargin = 70)

        /** Thermal paper that has spent a month in a wallet. */
        public val FADED: CaptureConditions = CaptureConditions(inkFade = 0.55)

        /** Handheld, badly. */
        public val BLURRY: CaptureConditions = CaptureConditions(blurRadius = 3, rotationDegrees = 2.0)
    }
}

/**
 * Draws a receipt onto an image, optionally spoiling it in realistic ways.
 *
 * Uses the JVM's logical monospaced font so the output is identical on a
 * developer's Mac and on a Linux CI runner with no fonts installed.
 */
public object ReceiptImageRenderer {

    // Sized so a rendered receipt is roughly the resolution of a phone photograph
    // of one. Smaller renders would flatter OCR in tests and mislead about accuracy.
    private const val FONT_SIZE = 30
    private const val PADDING = 60
    private const val LINE_SPACING = 1.28

    public fun render(spec: ReceiptSpec, conditions: CaptureConditions = CaptureConditions.CLEAN): BufferedImage =
        render(ReceiptTextRenderer.render(spec), conditions)

    public fun render(text: String, conditions: CaptureConditions): BufferedImage {
        val lines = text.lines()
        val font = Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE)

        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val metrics = probe.createGraphics().also { it.font = font }.fontMetrics
        val lineHeight = (metrics.height * LINE_SPACING).toInt()
        val widest = lines.maxOfOrNull { metrics.stringWidth(it) } ?: 1

        val paperWidth = widest + PADDING * 2
        val paperHeight = lineHeight * lines.size + PADDING * 2

        var image = BufferedImage(paperWidth, paperHeight, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color.WHITE
            fillRect(0, 0, paperWidth, paperHeight)
            color = Color(24, 24, 26)
            this.font = font
            lines.forEachIndexed { index, line ->
                drawString(line, PADDING, PADDING + metrics.ascent + index * lineHeight)
            }
            dispose()
        }

        if (conditions.perspective > 0.0) {
            image = photographAtAnAngle(image, conditions)
        }
        if (conditions.surroundMargin > 0 || abs(conditions.rotationDegrees) > 0.0) {
            image = placeOnSurface(image, conditions)
        }
        if (conditions.contrast != 1.0 || conditions.brightness != 0) {
            image = adjustTone(image, conditions.contrast, conditions.brightness)
        }
        if (conditions.inkFade > 0.0) {
            image = fadeInk(image, conditions.inkFade)
        }
        if (conditions.blurRadius > 0) {
            image = blur(image, conditions.blurRadius)
        }
        if (conditions.noise > 0.0) {
            image = addNoise(image, conditions.noise, conditions.seed)
        }
        return image
    }

    public fun toPng(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out ->
        ImageIO.write(image, "png", out)
        out.toByteArray()
    }

    public fun toJpeg(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out ->
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
            drawImage(image, 0, 0, null)
            dispose()
        }
        ImageIO.write(rgb, "jpg", out)
        out.toByteArray()
    }

    /** Tilts the page away from the camera, so its edges converge. */
    private fun photographAtAnAngle(paper: BufferedImage, conditions: CaptureConditions): BufferedImage {
        val inset = paper.width * conditions.perspective
        return PerspectiveWarp.apply(
            source = paper,
            destination = listOf(
                doubleArrayOf(inset, 0.0),
                doubleArrayOf(paper.width - 1.0, inset * 0.35),
                doubleArrayOf(paper.width - 1.0 - inset * 0.55, paper.height - 1.0),
                doubleArrayOf(0.0, paper.height - 1.0 - inset * 0.45),
            ),
            canvasWidth = paper.width,
            canvasHeight = paper.height,
            background = conditions.surroundColour,
        )
    }

    /** Puts the paper on a desk and turns it a little, as a phone photo would. */
    private fun placeOnSurface(paper: BufferedImage, conditions: CaptureConditions): BufferedImage {
        val margin = conditions.surroundMargin
        val radians = Math.toRadians(conditions.rotationDegrees)
        val rotatedWidth = (abs(paper.width * cos(radians)) + abs(paper.height * sin(radians))).toInt()
        val rotatedHeight = (abs(paper.width * sin(radians)) + abs(paper.height * cos(radians))).toInt()

        val canvas = BufferedImage(rotatedWidth + margin * 2, rotatedHeight + margin * 2, BufferedImage.TYPE_INT_RGB)
        canvas.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            color = if (margin > 0) conditions.surroundColour else Color.WHITE
            fillRect(0, 0, canvas.width, canvas.height)
            val transform = AffineTransform().apply {
                translate(canvas.width / 2.0, canvas.height / 2.0)
                rotate(radians)
                translate(-paper.width / 2.0, -paper.height / 2.0)
            }
            drawImage(paper, transform, null)
            dispose()
        }
        return canvas
    }

    private fun adjustTone(source: BufferedImage, contrast: Double, brightness: Int): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val rgb = source.getRGB(x, y)
                val channels = intArrayOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                val adjusted = channels.map { channel ->
                    (((channel - MID_GREY) * contrast + MID_GREY) + brightness)
                        .toInt().coerceIn(0, 255)
                }
                result.setRGB(x, y, (adjusted[0] shl 16) or (adjusted[1] shl 8) or adjusted[2])
            }
        }
        return result
    }

    /** Lightens ink toward the paper colour, leaving white paper white. */
    private fun fadeInk(source: BufferedImage, amount: Double): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val rgb = source.getRGB(x, y)
                val faded = intArrayOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF).map { channel ->
                    (channel + (255 - channel) * amount).toInt().coerceIn(0, 255)
                }
                result.setRGB(x, y, (faded[0] shl 16) or (faded[1] shl 8) or faded[2])
            }
        }
        return result
    }

    private fun blur(source: BufferedImage, radius: Int): BufferedImage {
        val size = radius * 2 + 1
        val weight = 1.0f / (size * size)
        val kernel = Kernel(size, size, FloatArray(size * size) { weight })
        val padded = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        padded.createGraphics().apply {
            drawImage(source, 0, 0, null)
            dispose()
        }
        return ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(padded, null)
    }

    private fun addNoise(source: BufferedImage, amount: Double, seed: Long): BufferedImage {
        val random = Random(seed)
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                var rgb = source.getRGB(x, y)
                if (random.nextDouble() < amount) {
                    val shift = random.nextInt(-NOISE_RANGE, NOISE_RANGE)
                    val channels = intArrayOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                        .map { (it + shift).coerceIn(0, 255) }
                    rgb = (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
                }
                result.setRGB(x, y, rgb)
            }
        }
        return result
    }

    private const val MID_GREY = 128
    private const val NOISE_RANGE = 45
}
