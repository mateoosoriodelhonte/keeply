package app.keeply.extraction

import app.keeply.domain.CurrencyCode
import app.keeply.domain.FieldConfidence
import app.keeply.domain.LineItem
import app.keeply.domain.Money

/**
 * Reads the things that were bought.
 *
 * A line item is a description with an amount at the end of it. The summary lines
 * at the bottom look identical in shape, so they are excluded by their labels
 * rather than by position: plenty of receipts print the total in the middle.
 *
 * Items are always marked uncertain. Nobody minds correcting a product name, and
 * claiming confidence about an abbreviated till description would be a stretch.
 */
public class LineItemParser(private val scanner: MoneyScanner = MoneyScanner()) {
    public fun parse(text: String, currency: CurrencyCode): List<LineItem> {
        val lines = text.lines()
        val items = mutableListOf<LineItem>()
        var pendingDescription: String? = null

        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank() || isRule(line)) {
                pendingDescription = null
                return@forEach
            }
            if (Labels.isSummaryLine(line)) {
                pendingDescription = null
                return@forEach
            }

            val amounts = scanner.scanLine(line)
            val description = describe(line, amounts)

            if (amounts.isEmpty()) {
                // A description on its own line, with its price on the next: how
                // multi-quantity items are usually printed.
                pendingDescription = description.takeIf { it.length >= MIN_DESCRIPTION }
                return@forEach
            }

            val quantityMatch = QUANTITY.find(line)
            val quantity = quantityMatch?.groupValues?.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                ?: QUANTITY.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val total = amounts.last().money
            val unit = if (amounts.size >= 2) amounts.first().money else null

            val text = when {
                description.length >= MIN_DESCRIPTION -> description
                pendingDescription != null -> pendingDescription
                else -> null
            } ?: run {
                pendingDescription = null
                return@forEach
            }

            items += LineItem(
                description = text,
                quantity = quantity?.takeIf { it in 1..MAX_QUANTITY },
                unitPrice = unit?.let { Money(it.amountMinor, currency) },
                totalPrice = Money(total.amountMinor, currency),
                confidence = FieldConfidence.UNCERTAIN,
            )
            pendingDescription = null
        }
        return items.take(MAX_ITEMS)
    }

    /** The line with its amounts and quantity markers removed. */
    private fun describe(line: String, amounts: List<MoneyMatch>): String {
        var text = line
        amounts.sortedByDescending { it.startColumn }.forEach { amount ->
            val end = (amount.endColumn + 1).coerceAtMost(text.length)
            val start = amount.startColumn.coerceAtMost(end)
            text = text.removeRange(start, end)
        }
        return text.replace(QUANTITY, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim('-', '*', '.', ':', '@')
            .trim()
    }

    private fun isRule(line: String): Boolean = line.trim().length >= 4 && line.trim().all { it in "-=_*~. " }

    private companion object {
        const val MIN_DESCRIPTION = 3
        const val MAX_QUANTITY = 999
        const val MAX_ITEMS = 200

        /** `3x`, `4 @`, `QTY 2`. */
        val QUANTITY = Regex("""\b(\d{1,3})\s*(?:x|@)\s?|\bqty\.?\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    }
}
