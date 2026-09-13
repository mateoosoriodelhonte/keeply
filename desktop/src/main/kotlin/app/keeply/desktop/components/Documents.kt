package app.keeply.desktop.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.keeply.documents.DataDirectory
import app.keeply.documents.PdfDocument
import app.keeply.domain.DocumentFormat
import app.keeply.domain.StoredDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** A document being shown, or the reason it cannot be. */
public sealed interface DocumentImage {
    public data object Loading : DocumentImage
    public data class Ready(val bitmap: ImageBitmap, val pageCount: Int) : DocumentImage
    public data class Failed(val message: String) : DocumentImage
}

/**
 * Loads a stored document for display, off the drawing thread.
 *
 * Reading is the only thing that happens: the file on disk is never written to,
 * moved or rewritten by being looked at. PDFs are rendered a page at a time rather
 * than all at once, because a manual can be a hundred pages and nobody is looking
 * at ninety-nine of them.
 */
@Composable
public fun rememberDocumentImage(
    directory: DataDirectory,
    document: StoredDocument?,
    page: Int = 0,
    maxWidth: Int = 1_400,
): DocumentImage {
    var state: DocumentImage by remember(document?.id, page) { mutableStateOf(DocumentImage.Loading) }

    LaunchedEffect(document?.id, page) {
        if (document == null) {
            state = DocumentImage.Failed("There is no document attached to this purchase.")
            return@LaunchedEffect
        }
        state = withContext(Dispatchers.IO) {
            runCatching {
                val file = directory.resolve(document.relativePath).toFile()
                val image: BufferedImage = if (document.format == DocumentFormat.PDF) {
                    PdfDocument.renderPage(file, page, dpi = PDF_DISPLAY_DPI)
                } else {
                    ImageIO.read(file) ?: error("unreadable image")
                }
                DocumentImage.Ready(scale(image, maxWidth).toComposeImageBitmap(), document.pageCount ?: 1)
            }.getOrElse {
                DocumentImage.Failed(
                    "Keeply could not open this file. It may have been moved or deleted outside the app.",
                )
            }
        }
    }
    return state
}

private fun scale(image: BufferedImage, maxWidth: Int): BufferedImage {
    if (image.width <= maxWidth) return image
    val ratio = maxWidth.toDouble() / image.width
    val height = (image.height * ratio).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply {
        setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        drawImage(image, 0, 0, maxWidth, height, null)
        dispose()
    }
    return scaled
}

/** Enough to read small print on screen without rendering a page at print resolution. */
private const val PDF_DISPLAY_DPI = 144f
