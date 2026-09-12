package app.keeply.extraction

import app.keeply.domain.FieldConfidence
import app.keeply.domain.Money

/** What the receipt says was paid, and how that was worked out. */
public data class TotalCandidate(
    val total: Money,
    val subtotal: Money?,
    val tax: Money?,
    val confidence: FieldConfidence,
    /** Which rule produced this, shown in the debug view. */
    val rule: String,
    val evidence: String,
)

/**
 * Works out what was actually paid.
 *
 * There is usually more than one plausible number on a receipt: a subtotal, a tax
 * line, the amount handed over, the change given back. Picking the largest one is
 * wrong often enough to matter, because cash tendered is frequently larger than the
 * total.
 *
 * The rules are ordered by how much evidence they rest on, and the arithmetic is
 * the strongest evidence available: if subtotal plus tax equals a labelled total,
 * that reading is almost certainly right and is marked confident. Everything weaker
 * is marked uncertain so the person is asked.
 */
public class TotalFinder(private val scanner: MoneyScanner = MoneyScanner()) {

    public fun find(text: String): TotalCandidate? {
        val lines = text.lines()
        val amountsByLine = lines.mapIndexed { index, line -> index to scanner.scanLine(line, index) }.toMap()

        val labelled = lines.mapIndexedNotNull { index, line ->
            val amounts = amountsByLine[index].orEmpty()
            if (amounts.isEmpty()) return@mapIndexedNotNull null
            // The rightmost amount on a labelled line is the value, not a rate such
            // as the "7.25%" in "Sales Tax (7.25%)".
            LabelledAmount(Labels.classify(line), amounts.last(), line.trim())
        }

        val totals = labelled.filter { it.label == LineLabel.TOTAL }
        val subtotal = labelled.lastOrNull { it.label == LineLabel.SUBTOTAL }?.amount?.money
        val tax = labelled.lastOrNull { it.label == LineLabel.TAX }?.amount?.money

        // Strongest: a labelled total that the rest of the receipt adds up to.
        if (subtotal != null && tax != null) {
            val expected = runCatching { subtotal + tax }.getOrNull()
            val agreeing = expected?.let { sum ->
                totals.firstOrNull {
                    it.amount.money.currency == sum.currency &&
                        it.amount.money.absoluteDifference(sum) <= ROUNDING_TOLERANCE
                }
            }
            if (agreeing != null) {
                return TotalCandidate(
                    total = agreeing.amount.money,
                    subtotal = subtotal,
                    tax = tax,
                    confidence = FieldConfidence.CONFIDENT,
                    rule = "labelled total, and subtotal plus tax agrees with it",
                    evidence = agreeing.line,
                )
            }
        }

        // A labelled total with no arithmetic to check it against. Common on
        // tax-inclusive receipts, which print no subtotal at all.
        val lastTotal = totals.lastOrNull()
        if (lastTotal != null) {
            val taxInclusive = subtotal == null && tax != null
            return TotalCandidate(
                total = lastTotal.amount.money,
                subtotal = subtotal,
                tax = tax,
                confidence = if (taxInclusive) FieldConfidence.CONFIDENT else FieldConfidence.UNCERTAIN,
                rule = if (taxInclusive) {
                    "labelled total on a tax-inclusive receipt"
                } else {
                    "labelled total, nothing to check it against"
                },
                evidence = lastTotal.line,
            )
        }

        // Nothing labelled. The last resort is the largest amount in the bottom part
        // of the receipt, ignoring anything that looks like cash handed over or
        // change given back.
        val fallback = amountsByLine.entries
            .filter { (index, _) -> index >= lines.size * BOTTOM_FRACTION }
            .filterNot { (index, _) ->
                Labels.classify(lines[index]) in setOf(LineLabel.CHANGE, LineLabel.TENDERED)
            }
            .flatMap { it.value }
            .filterNot { it.money.isNegative }
            .maxByOrNull { it.money.amountMinor }
            ?: return null

        return TotalCandidate(
            total = fallback.money,
            subtotal = subtotal,
            tax = tax,
            confidence = FieldConfidence.UNCERTAIN,
            rule = "largest amount near the bottom; nothing was labelled",
            evidence = lines.getOrElse(fallback.lineIndex) { "" }.trim(),
        )
    }

    private data class LabelledAmount(val label: LineLabel, val amount: MoneyMatch, val line: String)

    private companion object {
        /** A cent either way: tills round tax differently and OCR drops the odd digit. */
        const val ROUNDING_TOLERANCE = 2L
        const val BOTTOM_FRACTION = 0.4
    }
}
