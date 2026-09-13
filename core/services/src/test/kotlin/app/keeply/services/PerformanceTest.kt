package app.keeply.services

import app.keeply.documents.DataDirectory
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.imaging.Thumbnailer
import app.keeply.reminders.Notifier
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How Keeply behaves with a library somebody has actually used for a few years.
 *
 * The numbers are printed, and the assertions are generous ceilings rather than
 * targets: this exists to catch a regression that makes the application feel
 * slow, not to fail because a CI runner was busy. The figures quoted in the
 * documentation come from running this.
 *
 * Two thousand purchases is deliberately more than a person will have. Somebody
 * saving a receipt a day for five years reaches about eighteen hundred.
 */
class PerformanceTest {
    private val workspace: Path = createTempDirectory("keeply-perf")
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private val keeply = Keeply.open(
        directory = DataDirectory(workspace.resolve("keeply-data")),
        notifier = Notifier.disabled,
        clock = clock,
    )

    @AfterTest
    fun cleanUp() {
        keeply.close()
        workspace.toFile().deleteRecursively()
    }

    private fun fillLibrary(count: Int) {
        val generator = ReceiptGenerator(seed = 7)
        keeply.store.transaction {
            repeat(count) { index ->
                val spec = generator.spec(date = today.minusDays((index % 900).toLong()))
                keeply.purchases.save(
                    ReviewedPurchase(
                        productName = spec.items.first().description,
                        merchantName = spec.merchantName,
                        purchaseDate = spec.purchaseDate,
                        price = spec.total,
                        receiptNumber = spec.receiptNumber,
                        returnPolicy = app.keeply.domain.ReturnPolicy.Days(30),
                        returnPolicySource = app.keeply.domain.ReturnPolicySource.USER_ENTERED,
                        tags = listOf("bulk"),
                    ),
                    receiptText = app.keeply.fixtures.ReceiptTextRenderer.render(spec),
                )
            }
        }
    }

    @Test
    fun aLibraryOfTwoThousandStaysResponsive() {
        val purchases = 2_000
        val writeMillis = measureTimeMillis { fillLibrary(purchases) }

        val searchMillis = measureTimeMillis { repeat(SAMPLES) { keeply.library.search("headphones") } } / SAMPLES
        val filterMillis = measureTimeMillis { repeat(SAMPLES) { keeply.library.search("returnable") } } / SAMPLES
        val homeMillis = measureTimeMillis { repeat(SAMPLES) { keeply.library.needsAttention() } } / SAMPLES
        val listMillis = measureTimeMillis { repeat(SAMPLES) { keeply.store.purchases.recent(50) } } / SAMPLES
        val insightsMillis = measureTimeMillis { repeat(SAMPLES) { keeply.insights.summarise() } } / SAMPLES

        println(
            """
            purchases,$purchases
            writeAllMillis,$writeMillis
            searchMillis,$searchMillis
            filterMillis,$filterMillis
            needsAttentionMillis,$homeMillis
            recentFiftyMillis,$listMillis
            insightsMillis,$insightsMillis
            """.trimIndent(),
        )

        // Ceilings, not targets. Anything past these is felt rather than measured.
        assertTrue(searchMillis < SEARCH_CEILING, "full-text search took $searchMillis ms")
        assertTrue(filterMillis < FILTER_CEILING, "filtering took $filterMillis ms")
        assertTrue(homeMillis < HOME_CEILING, "the home screen's query took $homeMillis ms")
        assertTrue(listMillis < LIST_CEILING, "listing fifty purchases took $listMillis ms")
    }

    @Test
    fun openingALibraryIsFastEnoughToFeelInstant() {
        fillLibrary(500)
        keeply.close()

        val openMillis = measureTimeMillis {
            Keeply.open(DataDirectory(workspace.resolve("keeply-data")), Notifier.disabled, clock).close()
        }
        println("reopenMillis,$openMillis")
        // The language model is already unpacked by this point, which is the state
        // every launch after the first is in.
        assertTrue(openMillis < OPEN_CEILING, "opening took $openMillis ms")
    }

    @Test
    fun thumbnailsAreCheapEnoughToMakeOnImport() {
        val image = ReceiptImageRenderer.render(ReceiptGenerator(seed = 3).spec())
        val millis = measureTimeMillis {
            repeat(THUMBNAIL_SAMPLES) { Thumbnailer.toJpeg(Thumbnailer.thumbnail(image)) }
        } / THUMBNAIL_SAMPLES
        println("thumbnailMillis,$millis")
        assertTrue(millis < THUMBNAIL_CEILING, "a thumbnail took $millis ms")
    }

    @Test
    fun importingAPhotographIsWorthWaitingFor() = runBlocking {
        val spec = ReceiptGenerator(seed = 11).spec(date = today.minusDays(1))
        val file = workspace.resolve("perf.png")
        Files.write(file, ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec)))

        // Warms the OCR engine, which loads its model on first use. A person pays
        // that once per launch, not once per receipt.
        keeply.imports.import(file)

        val millis = measureTimeMillis { keeply.imports.import(file) }
        println("importMillis,$millis")
        assertTrue(millis < IMPORT_CEILING, "importing took $millis ms")
    }

    private companion object {
        const val SAMPLES = 20
        const val THUMBNAIL_SAMPLES = 10

        const val SEARCH_CEILING = 250L
        const val FILTER_CEILING = 400L
        const val HOME_CEILING = 250L
        const val LIST_CEILING = 150L
        const val OPEN_CEILING = 3_000L
        const val THUMBNAIL_CEILING = 250L
        const val IMPORT_CEILING = 6_000L
    }
}
