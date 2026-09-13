package app.keeply.desktop.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.GeneralPath
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * Draws Keeply's application icon.
 *
 * Generated rather than hand-drawn so it is reproducible, reviewable as a diff,
 * and regenerable at any size without an image editor. A receipt on a green tile:
 * the thing the application holds, in the colour the application uses for
 * "you are fine".
 */
public object IconArtwork {

    /** The sizes macOS wants in an iconset, as (pixels, filename) pairs. */
    private val ICONSET = listOf(
        16 to "icon_16x16.png",
        32 to "icon_16x16@2x.png",
        32 to "icon_32x32.png",
        64 to "icon_32x32@2x.png",
        128 to "icon_128x128.png",
        256 to "icon_128x128@2x.png",
        256 to "icon_256x256.png",
        512 to "icon_256x256@2x.png",
        512 to "icon_512x512.png",
        1_024 to "icon_512x512@2x.png",
    )

    public fun writeIconset(directory: Path): List<Path> {
        Files.createDirectories(directory)
        return ICONSET.map { (size, name) ->
            val target = directory.resolve(name)
            ImageIO.write(draw(size), "png", target.toFile())
            target
        }
    }

    public fun draw(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        val unit = size / 1_024.0

        // The tile. macOS insets its icons rather than filling the square, and an
        // icon that ignores that sits visibly larger than its neighbours in the Dock.
        val inset = 96 * unit
        val tile = RoundRectangle2D.Double(inset, inset, size - inset * 2, size - inset * 2, 200 * unit, 200 * unit)
        g.paint = GradientPaint(
            0f,
            inset.toFloat(),
            Color(0x24, 0x7A, 0x63),
            0f,
            (size - inset).toFloat(),
            Color(0x16, 0x50, 0x42),
        )
        g.fill(tile)

        // The receipt: a white slip with a torn bottom edge, which is the shape
        // people recognise without being told what it is.
        val paperWidth = 430 * unit
        val paperLeft = (size - paperWidth) / 2
        val paperTop = 265 * unit
        val paperBottom = 760 * unit
        val toothWidth = paperWidth / 7

        val paper = GeneralPath()
        paper.moveTo(paperLeft, paperTop)
        paper.lineTo(paperLeft + paperWidth, paperTop)
        paper.lineTo(paperLeft + paperWidth, paperBottom - 26 * unit)
        var x = paperLeft + paperWidth
        var up = false
        while (x > paperLeft) {
            x -= toothWidth
            paper.lineTo(x.coerceAtLeast(paperLeft), if (up) paperBottom - 26 * unit else paperBottom + 18 * unit)
            up = !up
        }
        paper.closePath()

        g.color = Color(0xFC, 0xFA, 0xF6)
        g.fill(paper)

        // Lines of print, shortest last, so it reads as a receipt rather than a page.
        g.color = Color(0x8C, 0x93, 0x8F)
        g.stroke = BasicStroke((26 * unit).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val lineLeft = paperLeft + 58 * unit
        listOf(0.72, 0.72, 0.46).forEachIndexed { index, fraction ->
            val y = paperTop + (112 + index * 92) * unit
            g.draw(
                java.awt.geom.Line2D.Double(lineLeft, y, lineLeft + (paperWidth - 116 * unit) * fraction, y),
            )
        }

        // A total, in the accent, because the number is the point.
        g.color = Color(0xC2, 0x7A, 0x1C)
        g.stroke = BasicStroke((34 * unit).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val totalY = paperTop + 420 * unit
        g.draw(
            java.awt.geom.Line2D.Double(
                paperLeft + paperWidth - 58 * unit - (paperWidth - 116 * unit) * 0.5,
                totalY,
                paperLeft + paperWidth - 58 * unit,
                totalY,
            ),
        )

        g.dispose()
        return image
    }
}

public fun main(args: Array<String>) {
    val target = Paths.get(args.getOrElse(0) { "build/Keeply.iconset" })
    val written = IconArtwork.writeIconset(target)
    println("Wrote ${written.size} icon sizes to ${target.toAbsolutePath()}")
}
