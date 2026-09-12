package app.keeply.extraction

/**
 * Undoes the specific mistakes OCR makes on receipt text.
 *
 * Tesseract confuses a small, fixed set of glyph pairs, and on a till receipt
 * those land in exactly the places that matter: a date read as `@5/09/2026`, a
 * total read as `$l2.5O`. Repair is applied only where the surrounding text says a
 * number was expected, so a shop genuinely called "SOHO" is never rewritten to
 * "5OHO".
 */
internal object OcrRepair {

    /** Glyphs Tesseract substitutes for digits, and what they should have been. */
    private val TO_DIGIT = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0', '@' to '0',
        'l' to '1', 'I' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'G' to '6', 'b' to '6',
        'T' to '7',
        'B' to '8',
        'g' to '9', 'q' to '9',
    )

    /** Rewrites a run that should be entirely numeric. */
    fun digitsOnly(token: String): String = token.map { TO_DIGIT[it] ?: it }.joinToString("")

    /**
     * Repairs a token only when it is mostly digits already.
     *
     * The threshold is what keeps words alone: `$l2.5O` is repaired, `TOTAL` is not.
     */
    fun repairNumeric(token: String, minimumDigitShare: Double = 0.5): String {
        val meaningful = token.filter { it.isLetterOrDigit() || it == '@' || it == '|' }
        if (meaningful.isEmpty()) return token
        val digits = meaningful.count(Char::isDigit)
        if (digits.toDouble() / meaningful.length < minimumDigitShare) return token
        return token.map { character ->
            if (character.isDigit() || character in SAFE_PUNCTUATION) character else TO_DIGIT[character] ?: character
        }.joinToString("")
    }

    /** True when a token is worth trying to read as a number at all. */
    fun looksNumeric(token: String): Boolean = token.any(Char::isDigit) || token.any { it in TO_DIGIT.keys && it in "Ol|IS@" }

    private const val SAFE_PUNCTUATION = ".,-()/$€£¥*# "
}
