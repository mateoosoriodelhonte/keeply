package app.keeply.extraction

import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import app.keeply.domain.MoneyParser

/** An amount found in receipt text, and where it was. */
public data class MoneyMatch(
    val money: Money,
    /** Exactly as it appeared, before any repair. */
    val raw: String,
    val lineIndex: Int,
    val startColumn: Int,
    val endColumn: Int,
    /** True when OCR damage had to be undone to read this. Lowers confidence. */
    val repaired: Boolean,
) {
    public val isRightAligned: Boolean get() = startColumn > 0
}

/**
 * Finds every amount of money in a block of receipt text.
 *
 * The hard part is not finding numbers, it is not finding the wrong ones. A till
 * receipt is full of digits that are not amounts: dates, times, phone numbers,
 * quantities, card suffixes, branch numbers. An amount is only accepted when it
 * carries a currency symbol or code, or has exactly two decimal places, and never
 * when it sits inside a date or a time.
 */
public class MoneyScanner(private val defaultCurrency: CurrencyCode = CurrencyCode.USD) {
    private val parser = MoneyParser(defaultCurrency)

    public fun scan(text: String): List<MoneyMatch> = text.lines().flatMapIndexed { index, line -> scanLine(line, index) }

    public fun scanLine(line: String, lineIndex: Int = 0): List<MoneyMatch> {
        val matches = mutableListOf<MoneyMatch>()
        val repaired = repairAmounts(line)

        AMOUNT.findAll(repaired).forEach { match ->
            val range = match.range
            if (insideDateOrTime(repaired, range)) return@forEach
            if (insideMaskedCard(repaired, range)) return@forEach

            val candidate = match.value.trim()
            val money = parser.parse(candidate) ?: return@forEach
            matches += MoneyMatch(
                money = money,
                raw = line.substring(range.first.coerceAtMost(line.length), (range.last + 1).coerceAtMost(line.length)),
                lineIndex = lineIndex,
                startColumn = range.first,
                endColumn = range.last,
                repaired = repaired != line,
            )
        }
        return matches
    }

    /**
     * Repairs glyphs inside things that look like amounts, before matching.
     *
     * Done per token and only where digits already dominate, so a shop name is
     * never mangled into a number.
     */
    private fun repairAmounts(line: String): String = line.split(' ').joinToString(" ") { token ->
        if (OcrRepair.looksNumeric(token) && token.any { it in "$€£¥" || it == '.' || it == ',' }) {
            OcrRepair.repairNumeric(token)
        } else {
            token
        }
    }

    /** `02/23/2026` and `20:36` are full of digits and none of them are money. */
    private fun insideDateOrTime(line: String, range: IntRange): Boolean {
        val before = line.getOrNull(range.first - 1)
        val after = line.getOrNull(range.last + 1)
        if (before in DATE_SEPARATORS || after in DATE_SEPARATORS) return true
        // A four-digit run that is a plausible year and carries no currency marker.
        val value = line.substring(range.first, range.last + 1)
        if (value.none { it in ".,$€£¥" } && value.length == 4 && value.all(Char::isDigit)) {
            val year = value.toInt()
            if (year in 1990..2100) return true
        }
        return false
    }

    /** `VISA ****1234` is a card suffix, not a price. */
    private fun insideMaskedCard(line: String, range: IntRange): Boolean {
        val prefix = line.substring(0, range.first)
        return prefix.endsWith("*") || prefix.endsWith("x") || prefix.endsWith("X")
    }

    private companion object {
        val DATE_SEPARATORS = setOf('/', ':', '-', '.')

        /**
         * Either a currency marker with a number, or a number with exactly two
         * decimal places. Anything looser matches quantities and branch numbers.
         */
        val AMOUNT = Regex(
            // A sign or bracket can sit either side of the currency symbol: tills
            // print both "-$5.00" and "$-5.00", and accounting style wraps the lot.
            """\(?-?(?:[$€£¥₹]|\b(?:USD|EUR|GBP|CAD|AUD|JPY|CHF|SEK|NZD)\b)\s?-?\d[\d.,]*\)?-?""" +
                """|\(?-?\d{1,3}(?:[,.]\d{3})*[.,]\d{2}\)?-?""",
        )
    }
}
