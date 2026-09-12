package app.keeply.extraction

import java.util.Locale

/** What a line of a receipt is announcing. */
internal enum class LineLabel {
    TOTAL,
    SUBTOTAL,
    TAX,
    DISCOUNT,
    CHANGE,
    TENDERED,
    NONE,
}

/**
 * Recognises the words tills print beside their numbers.
 *
 * The order matters more than the list does. "SUBTOTAL" contains "TOTAL", and an
 * extractor that checks for "TOTAL" first will confidently report the subtotal as
 * the amount paid, which is exactly the kind of quiet wrongness Keeply must not do.
 * Subtotals are therefore matched first, and every lookup here goes through this
 * one function.
 */
internal object Labels {

    fun classify(line: String): LineLabel {
        val normalised = line.uppercase(Locale.ROOT).filter { it.isLetter() || it == ' ' }.trim()
        if (normalised.isEmpty()) return LineLabel.NONE

        // Checked before TOTAL, deliberately.
        if (matches(normalised, SUBTOTAL)) return LineLabel.SUBTOTAL
        if (matches(normalised, CHANGE)) return LineLabel.CHANGE
        if (matches(normalised, TENDERED)) return LineLabel.TENDERED
        if (matches(normalised, DISCOUNT)) return LineLabel.DISCOUNT
        if (matches(normalised, TAX)) return LineLabel.TAX
        if (matches(normalised, TOTAL)) return LineLabel.TOTAL
        return LineLabel.NONE
    }

    /**
     * Whole words only.
     *
     * A plain substring test classified the receipt number `TXN-998123` as a tax
     * line, because "TXN" contains the abbreviation "TX". Till abbreviations are
     * short enough that they collide with ordinary text constantly.
     */
    private fun matches(normalised: String, labels: List<Regex>): Boolean = labels.any { it.containsMatchIn(normalised) }

    private fun words(vararg labels: String): List<Regex> = labels.map { Regex("""\b${Regex.escape(it)}\b""") }

    /** Words that mean this line is not an item, whatever else is on it. */
    fun isSummaryLine(line: String): Boolean = classify(line) != LineLabel.NONE

    private val SUBTOTAL = words("SUBTOTAL", "SUB TOTAL", "SUBTTL", "SUBTL", "SUB TTL", "NET TOTAL")
    private val TOTAL = words(
        "GRAND TOTAL", "TOTAL DUE", "AMOUNT DUE", "BALANCE DUE", "AMOUNT PAID",
        "TOTAL", "TOTL", "TTL", "AMOUNT", "BALANCE", "TO PAY",
    )
    private val TAX = words("SALES TAX", "TAX", "VAT", "GST", "HST", "PST", "INCLUDES TAX", "TX")
    private val DISCOUNT = words("DISCOUNT", "SAVINGS", "COUPON", "PROMO")
    private val CHANGE = words("CHANGE DUE", "CHANGE")
    private val TENDERED = words("TENDERED", "CASH TENDER", "AMOUNT TENDERED", "CARD PAYMENT", "PAID BY")
}
