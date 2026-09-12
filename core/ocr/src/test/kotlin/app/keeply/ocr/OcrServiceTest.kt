package app.keeply.ocr

import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OcrServiceTest {
    private val generator = ReceiptGenerator(seed = 12)
    private val image = ReceiptImageRenderer.render(generator.spec(layout = ReceiptLayout.COMPACT))

    private fun service(concurrency: Int = 2) = OcrService(OcrTestSupport.tessdata, maxConcurrency = concurrency)

    @Test
    fun readsAnImage() = runBlocking {
        service().use { ocr ->
            val result = ocr.read(image)
            assertTrue(result.text.isNotBlank())
            assertTrue(result.meanConfidence > 0)
            assertContains(result.engine, "tesseract")
        }
    }

    @Test
    fun reportsProgressInAnOrderThatMakesSense() = runBlocking {
        service().use { ocr ->
            val seen = mutableListOf<OcrProgress>()
            ocr.read(image) { seen += it }
            assertTrue(seen.first() is OcrProgress.Queued)
            assertTrue(seen.any { it is OcrProgress.Reading })
            assertTrue(seen.last() is OcrProgress.Finished)
        }
    }

    @Test
    fun neverRunsMoreEnginesThanItPromised() = runBlocking {
        // Dropping twenty receipts at once must not start twenty Tesseract
        // instances, each of which wants its own memory and its own core.
        val concurrent = AtomicInteger()
        val peak = AtomicInteger()

        service(concurrency = 2).use { ocr ->
            coroutineScope {
                List(8) {
                    async(Dispatchers.Default) {
                        ocr.read(image) { progress ->
                            if (progress is OcrProgress.Reading) {
                                val now = concurrent.incrementAndGet()
                                peak.updateAndGet { previous -> maxOf(previous, now) }
                            }
                            if (progress is OcrProgress.Finished) concurrent.decrementAndGet()
                        }
                    }
                }.awaitAll()
            }
        }
        assertTrue(peak.get() <= 2, "peaked at ${peak.get()} concurrent readings")
    }

    @Test
    fun stopsWhenTheImportScreenIsClosed() = runBlocking {
        // Cancellation has to actually stop the work, not leave it running out of sight.
        service(concurrency = 1).use { ocr ->
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                ocr.readPages(List(40) { image }) { progress ->
                    if (progress is OcrProgress.Reading && progress.pageNumber == 1) started.complete(Unit)
                }
            }
            withTimeout(30_000) { started.await() }
            job.cancel()
            withTimeout(30_000) { job.join() }
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun readsEveryPageOfADocumentAndJoinsThem() = runBlocking {
        service().use { ocr ->
            val pages = List(3) { image }
            val seen = mutableListOf<Int>()
            val result = ocr.readPages(pages) { progress ->
                if (progress is OcrProgress.Reading) seen += progress.pageNumber
            }
            assertEquals(listOf(1, 2, 3), seen)
            assertTrue(result.durationMillis > 0)
            assertTrue(result.words.size > pages.first().let { 1 })
        }
    }

    @Test
    fun anEmptyDocumentReadsAsEmptyRatherThanFailing() = runBlocking {
        service().use { ocr ->
            assertEquals(OcrResult.empty, ocr.readPages(emptyList()))
        }
    }

    @Test
    fun refusesToBeUsedAfterItIsClosed() = runBlocking {
        val engine = TesseractEngine(OcrTestSupport.tessdata)
        engine.close()
        assertFailsWith<IllegalStateException> { engine.read(image) }
    }

    @Test
    fun defaultConcurrencyLeavesRoomForTheInterface() {
        val concurrency = OcrService.defaultConcurrency()
        assertTrue(concurrency in 1..2, "was $concurrency")
    }

    @Test
    fun keepsTheCallingThreadFreeWhileItWorks() = runBlocking {
        // OCR must not block whatever is drawing the interface.
        service().use { ocr ->
            var ticks = 0
            val ticker = launch(Dispatchers.Default) {
                while (true) {
                    ticks++
                    delay(5)
                }
            }
            withContext(Dispatchers.Default) { ocr.readPages(List(3) { image }) }
            ticker.cancel()
            assertTrue(ticks > 3, "the caller only got $ticks ticks while OCR ran")
        }
    }
}
