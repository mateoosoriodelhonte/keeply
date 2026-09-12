package app.keeply.ocr

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * How Tesseract should read the page.
 *
 * Receipts are a single narrow column, and telling Tesseract that outperforms
 * letting it work the layout out for itself. The default here was chosen by
 * measuring against the synthetic corpus, not by copying a blog post; see
 * `OcrAccuracyTest` and `docs/OCR_PIPELINE.md`.
 */
public enum class PageLayout(internal val mode: Int) {
    /** One uniform block of text. Best for a cropped receipt. */
    SINGLE_BLOCK(tesseract.PSM_SINGLE_BLOCK),

    /** A single column with varying text sizes. */
    SINGLE_COLUMN(tesseract.PSM_SINGLE_COLUMN),

    /** Let Tesseract work it out. Better for a page that is not just a receipt. */
    AUTOMATIC(tesseract.PSM_AUTO),

    /** Sparse text with no particular order. */
    SPARSE(tesseract.PSM_SPARSE_TEXT),
}

/**
 * A local Tesseract instance.
 *
 * Nothing here touches the network. The image never leaves the machine, and the
 * only thing this reads from disk is the language model Keeply shipped.
 *
 * Not safe to use from more than one thread: a `TessBaseAPI` holds the page it is
 * working on. [OcrService] owns a pool of these and hands out one per job.
 */
public class TesseractEngine(
    tessdataDirectory: Path,
    private val language: String = TessdataInstaller.DEFAULT_LANGUAGE,
    private val layout: PageLayout = PageLayout.SINGLE_BLOCK,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(TesseractEngine::class.java)
    private val api = TessBaseAPI()
    private var closed = false

    init {
        val path = tessdataDirectory.toAbsolutePath().toString()
        if (api.Init(path, language) != 0) {
            api.close()
            throw OcrUnavailableException(
                "Keeply could not start its text recognition. The application may not have installed correctly.",
            )
        }
        api.SetPageSegMode(layout.mode)
        // Keeps the column alignment of a till receipt, which is most of what makes
        // a total findable next to its label.
        api.SetVariable("preserve_interword_spaces", "1")
    }

    public val version: String get() = TessBaseAPI.Version().string

    public val engineName: String get() = "tesseract-$version/$language/${layout.name.lowercase()}"

    /**
     * Reads an image.
     *
     * The image is converted to eight-bit grey and handed to Tesseract directly,
     * with no temporary file, so a receipt is never written anywhere unencrypted on
     * its way through OCR.
     */
    public fun read(image: BufferedImage): OcrResult {
        check(!closed) { "This OCR engine has been closed" }
        val started = System.nanoTime()
        val grey = toGreyBytes(image)
        val pointer = BytePointer(*grey)

        return try {
            api.SetImage(pointer, image.width, image.height, 1, image.width)
            val text = api.GetUTF8Text() ?: return OcrResult.empty
            val body = try {
                text.string
            } finally {
                text.deallocate()
            }
            val words = readWords()
            val elapsed = (System.nanoTime() - started) / 1_000_000
            OcrResult(
                text = body,
                words = words,
                meanConfidence = if (words.isEmpty()) api.MeanTextConf().toDouble() else words.map { it.confidence }.average(),
                engine = engineName,
                durationMillis = elapsed,
            )
        } catch (e: RuntimeException) {
            log.warn("Tesseract failed to read a {}x{} image", image.width, image.height, e)
            throw OcrUnavailableException(
                "Keeply could not read that image. It may be damaged. You can still enter the details yourself.",
                e,
            )
        } finally {
            api.Clear()
            pointer.deallocate()
        }
    }

    private fun readWords(): List<OcrWord> {
        val iterator = api.GetIterator() ?: return emptyList()
        val words = mutableListOf<OcrWord>()
        try {
            val level = tesseract.RIL_WORD
            do {
                val wordPointer = iterator.GetUTF8Text(level) ?: continue
                val word = try {
                    wordPointer.string
                } finally {
                    wordPointer.deallocate()
                }
                if (word.isBlank()) continue

                val left = IntArray(1)
                val top = IntArray(1)
                val right = IntArray(1)
                val bottom = IntArray(1)
                iterator.BoundingBox(level, left, top, right, bottom)

                words += OcrWord(
                    text = word.trim(),
                    confidence = iterator.Confidence(level),
                    box = TextBox(left[0], top[0], right[0], bottom[0]),
                )
            } while (iterator.Next(level))
        } finally {
            iterator.close()
        }
        return words
    }

    /** Eight-bit grey, the format Tesseract wants, with no intermediate file. */
    private fun toGreyBytes(image: BufferedImage): ByteArray {
        val bytes = ByteArray(image.width * image.height)
        var index = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF
                // Rec. 601 luma: matches how the eye weights the channels.
                bytes[index++] = ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
            }
        }
        return bytes
    }

    override fun close() {
        if (closed) return
        closed = true
        api.End()
        api.close()
    }
}
