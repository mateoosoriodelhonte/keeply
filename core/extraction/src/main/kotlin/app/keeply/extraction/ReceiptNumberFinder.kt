package app.keeply.extraction

import app.keeply.domain.FieldConfidence

public data class ReceiptNumberCandidate(val number: String, val confidence: FieldConfidence, val rule: String, val evidence: String)

/**
 * Finds the receipt or order number.
 *
 * Worth getting right beyond tidiness: it is the strongest duplicate signal
 * Keeply has, and the thing a shop asks for when someone brings something back.
 *
 * A labelled number is trusted. A bare code is not, because a till prints plenty
 * of other codes: register numbers, cashier ids, loyalty numbers.
 */
public class ReceiptNumberFinder {

    public fun find(text: String): ReceiptNumberCandidate? {
        text.lines().forEach { line ->
            LABELLED.find(line)?.let { match ->
                // A leading hash is a separator the till prints, not part of the number.
                val value = match.groupValues[2].trim().removePrefix("#").trim(':', '-').trim()
                if (isPlausible(value)) {
                    return ReceiptNumberCandidate(
                        number = value,
                        confidence = FieldConfidence.CONFIDENT,
                        rule = "labelled '${match.groupValues[1].trim()}'",
                        evidence = line.trim(),
                    )
                }
            }
        }

        text.lines().forEach { line ->
            if (Labels.isSummaryLine(line)) return@forEach
            STANDALONE.find(line)?.let { match ->
                val value = match.value.trim().removePrefix("#")
                if (isPlausible(value)) {
                    return ReceiptNumberCandidate(
                        number = value,
                        confidence = FieldConfidence.UNCERTAIN,
                        rule = "a code that looks like a receipt number, but was not labelled",
                        evidence = line.trim(),
                    )
                }
            }
        }
        return null
    }

    private fun isPlausible(value: String): Boolean {
        if (value.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (value.none(Char::isDigit)) return false
        // A bare four-digit number is as likely to be a year or a register number.
        if (value.length == 4 && value.all(Char::isDigit)) return false
        return true
    }

    private companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 32

        val LABELLED = Regex(
            """\b(receipt|transaction|trans|order|invoice(?:\s+number)?|ref(?:erence)?|ticket|sale)\b""" +
                // The value may itself begin with a word, as in "Transaction: ORDER 44259208",
                // and may open with a hash, as in "Invoice number : #6140-93".
                """\s*(?:no\.?|number|:)?\s*(#?[A-Za-z0-9][A-Za-z0-9\-/#]{2,20}(?:\s+\d{3,12})?)""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * `TXN-214034`, `#4418-45`, `R671247`.
         *
         * A lookbehind rather than `\b`, because a hash is not a word character and
         * a boundary before it never matches.
         */
        val STANDALONE = Regex("""(?<![\w#])(?:[A-Z]{1,4}[-#]?\d{4,}|#\d{3,}-\d{2,})\b""")
    }
}
