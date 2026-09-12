package app.keeply.extraction

import app.keeply.domain.FieldSource
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptSpec
import app.keeply.fixtures.ReceiptTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a receipt Keeply reads correctly, across a corpus whose contents are
 * known exactly.
 *
 * This is deterministic extraction measured on perfect text, which separates
 * parsing mistakes from OCR mistakes. The end-to-end figure, with real OCR in
 * front of it, is measured in the services module.
 *
 * The thresholds come from running this. They sit a little below the measured
 * figures so ordinary variation does not fail the build, and close enough that a
 * regression does.
 */
class ExtractionAccuracyTest {
    private val extractor = ReceiptExtractor()
    private val corpus: List<ReceiptSpec> = ReceiptGenerator(seed = 2_026_0912).corpus(CORPUS_SIZE)

    private fun extract(spec: ReceiptSpec) = extractor.extract(
        ExtractionInput(
            text = ReceiptTextRenderer.render(spec),
            source = FieldSource.PDF_TEXT,
            defaultCurrency = spec.currency,
            today = spec.purchaseDate.plusDays(1),
        ),
    )

    @Test
    fun readsTheTotalOnEveryReceipt() {
        // The one field nobody will forgive Keeply for getting wrong.
        val wrong = corpus.mapNotNull { spec ->
            val total = extract(spec).draft.total?.value
            if (total?.amountMinor == spec.totalMinor) null else "${spec.layout}: expected ${spec.total}, got $total"
        }
        assertEquals(emptyList(), wrong, "totals misread on ${wrong.size} of ${corpus.size} receipts")
    }

    @Test
    fun readsTheDateOnAlmostEveryReceipt() {
        val correct = corpus.count { spec -> extract(spec).draft.purchaseDate?.value == spec.purchaseDate }
        val rate = correct.toDouble() / corpus.size
        println("purchase date: %d/%d (%.1f%%)".format(correct, corpus.size, rate * 100))
        assertTrue(rate >= DATE_FLOOR, "dates read on $correct of ${corpus.size}")
    }

    @Test
    fun readsTheShopOnAlmostEveryReceipt() {
        val correct = corpus.count { spec ->
            extract(spec).draft.merchantName?.value.equals(spec.merchantName, ignoreCase = true)
        }
        val rate = correct.toDouble() / corpus.size
        println("merchant: %d/%d (%.1f%%)".format(correct, corpus.size, rate * 100))
        assertTrue(rate >= MERCHANT_FLOOR, "shops read on $correct of ${corpus.size}")
    }

    @Test
    fun readsTheReceiptNumberOnMostReceipts() {
        val correct = corpus.count { spec ->
            extract(spec).draft.receiptNumber?.value?.contains(spec.receiptNumber.takeLast(RECEIPT_TAIL)) == true
        }
        val rate = correct.toDouble() / corpus.size
        println("receipt number: %d/%d (%.1f%%)".format(correct, corpus.size, rate * 100))
        assertTrue(rate >= RECEIPT_NUMBER_FLOOR, "receipt numbers read on $correct of ${corpus.size}")
    }

    @Test
    fun readsTheTaxWhenTheReceiptPrintsOne() {
        // Only receipts that actually print a tax line. A cramped till slip with
        // tax-inclusive prices prints none, and reporting a tax figure from one
        // would mean inventing it.
        val printsTax = corpus.filter { spec ->
            ReceiptTextRenderer.render(spec).lines().any { Labels.classify(it) == LineLabel.TAX }
        }
        val correct = printsTax.count { spec -> extract(spec).draft.tax?.value?.amountMinor == spec.taxMinor }
        val rate = correct.toDouble() / printsTax.size
        println("tax: %d/%d (%.1f%%)".format(correct, printsTax.size, rate * 100))
        assertTrue(rate >= TAX_FLOOR, "tax read on $correct of ${printsTax.size}")
    }

    @Test
    fun saysNothingAboutTaxWhenTheReceiptPrintsNone() {
        val silent = corpus.filter { spec ->
            ReceiptTextRenderer.render(spec).lines().none { Labels.classify(it) == LineLabel.TAX }
        }
        silent.forEach { spec ->
            assertEquals(null, extract(spec).draft.tax, "${spec.layout} prints no tax line, so none should be reported")
        }
    }

    @Test
    fun isNeverConfidentlyWrongAboutADate() {
        // The guarantee that matters more than the hit rate. Some receipts write
        // 08.10.26 and there is no way to know which number is the month, so Keeply
        // marks those uncertain and asks. What it must never do is state one and be
        // wrong.
        corpus.forEach { spec ->
            val field = extract(spec).draft.purchaseDate ?: return@forEach
            if (field.value != spec.purchaseDate) {
                assertTrue(
                    field.needsReview,
                    "${spec.layout} read ${field.value} instead of ${spec.purchaseDate} without flagging it",
                )
            }
        }
    }

    @Test
    fun findsTheItemsThatWereBought() {
        val found = corpus.count { spec -> extract(spec).draft.lineItems.size == spec.items.size }
        val rate = found.toDouble() / corpus.size
        println("item count exact: %d/%d (%.1f%%)".format(found, corpus.size, rate * 100))
        assertTrue(rate >= ITEMS_FLOOR, "item counts matched on $found of ${corpus.size}")
    }

    @Test
    fun neverInventsAFieldItCouldNotRead() {
        // The rule the whole design rests on. Nonsense in, nothing out.
        val outcome = extractor.extract(
            ExtractionInput(
                text = "qqq www eee\nrrr ttt yyy\nuuu iii ooo",
                source = FieldSource.OCR,
            ),
        )
        val draft = outcome.draft
        assertEquals(null, draft.total)
        assertEquals(null, draft.purchaseDate)
        assertEquals(null, draft.receiptNumber)
        assertTrue(draft.lineItems.isEmpty())
        assertTrue(ExtractionOutcome.NOT_FOUND in outcome.notes.map { it.outcome })
    }

    @Test
    fun explainsWhereEveryValueCameFrom() {
        // What the debug view shows, and what makes the review screen worth reading.
        val outcome = extract(corpus.first { it.layout == app.keeply.fixtures.ReceiptLayout.CLASSIC_TILL })
        val total = outcome.notes.single { it.field == "Total" }
        assertTrue(total.rule.isNotBlank())
        assertTrue(total.evidence?.isNotBlank() == true, "a total should cite the line it was read from")
        println(outcome.notes.joinToString("\n") { "${it.field}: ${it.outcome}  (${it.rule})" })
    }

    @Test
    fun trustsTextFromAPdfMoreThanTextFromAPhotograph() {
        val spec = corpus.first()
        val text = ReceiptTextRenderer.render(spec)
        val fromPdf = extractor.extract(
            ExtractionInput(text, FieldSource.PDF_TEXT, defaultCurrency = spec.currency, today = spec.purchaseDate.plusDays(1)),
        )
        val fromPhoto = extractor.extract(
            ExtractionInput(text, FieldSource.OCR, defaultCurrency = spec.currency, today = spec.purchaseDate.plusDays(1)),
        )
        val pdfConfident = fromPdf.draft.let { listOfNotNull(it.merchantName, it.total, it.receiptNumber) }
            .count { !it.needsReview }
        val photoConfident = fromPhoto.draft.let { listOfNotNull(it.merchantName, it.total, it.receiptNumber) }
            .count { !it.needsReview }
        assertTrue(pdfConfident >= photoConfident, "a text layer was never guessed at, so it starts stronger")
    }

    private companion object {
        const val CORPUS_SIZE = 40
        const val RECEIPT_TAIL = 5

        // Measured at 100% for every field on this corpus. The floors sit just
        // below so ordinary variation does not fail the build, and close enough
        // that a regression does.
        const val DATE_FLOOR = 0.95
        const val MERCHANT_FLOOR = 0.95
        const val RECEIPT_NUMBER_FLOOR = 0.95
        const val TAX_FLOOR = 0.95
        const val ITEMS_FLOOR = 0.95
    }
}
