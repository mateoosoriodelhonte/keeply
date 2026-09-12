package app.keeply.ocr

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** How far along a reading is, for the progress shown during import. */
public sealed interface OcrProgress {
    public data object Queued : OcrProgress
    public data class Reading(val pageNumber: Int, val pageCount: Int) : OcrProgress
    public data class Finished(val meanConfidence: Double, val durationMillis: Long) : OcrProgress
}

/**
 * Runs OCR without letting it take over the machine.
 *
 * Someone dropping twenty receipts at once must not start twenty Tesseract
 * instances: each one is expensive in both memory and CPU, and the result would be
 * a frozen application. Work is bounded by a small pool of engines, and jobs queue
 * for one rather than spawning their own.
 *
 * Every suspension point checks for cancellation, so closing the import screen
 * actually stops the work rather than leaving it running in the background.
 */
public class OcrService(
    private val tessdataDirectory: Path,
    private val language: String = TessdataInstaller.DEFAULT_LANGUAGE,
    private val layout: PageLayout = PageLayout.SINGLE_BLOCK,
    maxConcurrency: Int = defaultConcurrency(),
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(OcrService::class.java)

    private val permits = Semaphore(maxConcurrency)
    private val engines = ConcurrentLinkedQueue<TesseractEngine>()
    private val engineCount = AtomicInteger()
    private val dispatcher: CoroutineDispatcher =
        Executors.newFixedThreadPool(maxConcurrency) { runnable ->
            Thread(runnable, "keeply-ocr-${engineCount.incrementAndGet()}").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    public val concurrency: Int = maxConcurrency

    /** Reads one image. Suspends until an engine is free. */
    public suspend fun read(image: BufferedImage, onProgress: (OcrProgress) -> Unit = {}): OcrResult {
        onProgress(OcrProgress.Queued)
        return permits.withPermit {
            coroutineContext.ensureActive()
            withContext(dispatcher) {
                onProgress(OcrProgress.Reading(1, 1))
                val engine = borrow()
                try {
                    engine.read(image).also {
                        onProgress(OcrProgress.Finished(it.meanConfidence, it.durationMillis))
                    }
                } finally {
                    release(engine)
                }
            }
        }
    }

    /**
     * Reads every page of a document and joins the text.
     *
     * Cancellation is checked between pages, so a long PDF stops promptly when the
     * person closes the screen.
     */
    public suspend fun readPages(pages: List<BufferedImage>, onProgress: (OcrProgress) -> Unit = {}): OcrResult {
        if (pages.isEmpty()) return OcrResult.empty
        onProgress(OcrProgress.Queued)

        val results = mutableListOf<OcrResult>()
        pages.forEachIndexed { index, page ->
            coroutineContext.ensureActive()
            onProgress(OcrProgress.Reading(index + 1, pages.size))
            results += permits.withPermit {
                withContext(dispatcher) {
                    val engine = borrow()
                    try {
                        engine.read(page)
                    } finally {
                        release(engine)
                    }
                }
            }
        }

        val combined = OcrResult(
            text = results.joinToString("\n\n") { it.text },
            words = results.flatMap { it.words },
            meanConfidence = results.filter { !it.isEmpty }.map { it.meanConfidence }.averageOrZero(),
            engine = results.first().engine,
            durationMillis = results.sumOf { it.durationMillis },
        )
        onProgress(OcrProgress.Finished(combined.meanConfidence, combined.durationMillis))
        return combined
    }

    private fun borrow(): TesseractEngine = engines.poll() ?: TesseractEngine(tessdataDirectory, language, layout).also {
        log.debug("Started an OCR engine ({})", it.engineName)
    }

    private fun release(engine: TesseractEngine) {
        engines.offer(engine)
    }

    override fun close() {
        while (true) {
            val engine = engines.poll() ?: break
            runCatching { engine.close() }
        }
        (dispatcher as? AutoCloseable)?.let { runCatching { it.close() } }
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    public companion object {
        /**
         * Two engines by default.
         *
         * Tesseract is memory-hungry and largely single-threaded, and the machine
         * still has an interface to draw. More engines make a batch finish barely
         * sooner while making the application feel worse the whole time.
         */
        public fun defaultConcurrency(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)
    }
}
