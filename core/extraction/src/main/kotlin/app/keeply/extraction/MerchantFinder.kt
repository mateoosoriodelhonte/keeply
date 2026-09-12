package app.keeply.extraction

import app.keeply.domain.FieldConfidence
import app.keeply.domain.Merchant

/** The shop, and how it was identified. */
public data class MerchantCandidate(
    val name: String,
    val matchedMerchant: Merchant?,
    val confidence: FieldConfidence,
    val rule: String,
    val evidence: String,
)

/**
 * Works out which shop a receipt is from.
 *
 * A saved merchant match is the strongest signal, because it also brings the
 * person's own return-window rule with it. Failing that, the shop name is almost
 * always the first line of real text: tills print it above the address, and the
 * address is recognisable by its own shape.
 */
public class MerchantFinder {

    public fun find(text: String, knownMerchants: List<Merchant> = emptyList()): MerchantCandidate? {
        val headerLines = text.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !isRule(it) }
            .take(HEADER_LINES)
            .toList()
        if (headerLines.isEmpty()) return null

        // A shop the person has saved: exact match on a normalised name.
        headerLines.forEach { line ->
            val key = Merchant.normaliseName(line)
            if (key.isBlank()) return@forEach
            knownMerchants.firstOrNull { merchant -> merchant.matchKeys.any { it == key } }?.let { merchant ->
                return MerchantCandidate(
                    name = merchant.name,
                    matchedMerchant = merchant,
                    confidence = FieldConfidence.CONFIDENT,
                    rule = "matched a shop you have saved",
                    evidence = line,
                )
            }
        }

        // A saved name appearing inside a longer till header, which usually carries
        // a branch number or a city. Worth offering, not worth asserting.
        headerLines.forEach { line ->
            val key = Merchant.normaliseName(line)
            if (key.isBlank()) return@forEach
            knownMerchants.firstOrNull { merchant ->
                merchant.matchKeys.any { it.length >= MIN_SUBSTRING && key.contains(it) }
            }?.let { merchant ->
                return MerchantCandidate(
                    name = merchant.name,
                    matchedMerchant = merchant,
                    confidence = FieldConfidence.UNCERTAIN,
                    rule = "a shop you have saved appears in the receipt header",
                    evidence = line,
                )
            }
        }

        val name = headerLines.firstOrNull { line -> isPlausibleName(line) } ?: return null
        return MerchantCandidate(
            name = tidy(name),
            matchedMerchant = null,
            confidence = FieldConfidence.UNCERTAIN,
            rule = "first line of the receipt that reads like a shop name",
            evidence = name,
        )
    }

    /**
     * Rejects the lines that sit near a shop name but are not one: street
     * addresses, phone numbers, dates, and anything that is mostly digits.
     */
    private fun isPlausibleName(line: String): Boolean {
        if (line.length < MIN_NAME_LENGTH || line.length > MAX_NAME_LENGTH) return false
        val letters = line.count(Char::isLetter)
        if (letters < MIN_LETTERS) return false
        if (letters.toDouble() / line.length < MIN_LETTER_SHARE) return false
        if (Labels.isSummaryLine(line)) return false
        if (ADDRESS.containsMatchIn(line)) return false
        if (PHONE.containsMatchIn(line)) return false
        if (line.contains('@') && line.contains('.')) return false
        return true
    }

    /** Till headers shout; the library does not have to. */
    private fun tidy(name: String): String {
        val cleaned = name.trim().trim('*', '-', '=', '.').trim()
        if (cleaned.any { it.isLowerCase() }) return cleaned
        return cleaned.split(' ').joinToString(" ") { word ->
            when {
                word.length <= SHORT_WORD && word.all(Char::isLetter) -> word
                else -> word.lowercase().replaceFirstChar(Char::uppercaseChar)
            }
        }
    }

    private fun isRule(line: String): Boolean = line.length >= 4 && line.all { it in "-=_*~. " }

    private companion object {
        const val HEADER_LINES = 6
        const val MIN_NAME_LENGTH = 3
        const val MAX_NAME_LENGTH = 60
        const val MIN_LETTERS = 3
        const val MIN_LETTER_SHARE = 0.5
        const val MIN_SUBSTRING = 4
        const val SHORT_WORD = 3

        /** A street line: a number followed by words, or a postal code shape. */
        val ADDRESS = Regex(
            """^\d+\s+\p{L}|\b(?:street|st|road|rd|avenue|ave|lane|ln|drive|dr|way|suite|floor|unit|highway|hwy)\b\.?""" +
                """|\b[A-Z]{2}\s+\d{5}\b|\b\d{5}(?:-\d{4})?$""",
            RegexOption.IGNORE_CASE,
        )
        val PHONE = Regex("""\(?\d{3}\)?[\s.-]?\d{3,4}[\s.-]?\d{0,4}""")
    }
}
