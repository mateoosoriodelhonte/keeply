package app.keeply.domain

import java.util.Locale

/**
 * A shop the person buys from.
 *
 * Merchants exist so someone can save their own default return window once instead
 * of typing it for every purchase. That default is theirs: Keeply presents it as
 * "your saved rule", never as the retailer's policy, because Keeply has no way to
 * know what a retailer's policy is today.
 */
public data class Merchant(
    val id: MerchantId,
    val name: String,
    /** Lowercase, punctuation-free form used for matching receipt text. */
    val matchKey: String = normaliseName(name),
    /** Extra spellings seen on receipts, for example an abbreviated till name. */
    val aliases: List<String> = emptyList(),
    /** The person's own default return window for this shop. */
    val defaultReturnPolicy: ReturnPolicy = ReturnPolicy.Unknown,
    val notes: String? = null,
) {
    init {
        require(name.isNotBlank()) { "A merchant needs a name" }
    }

    /** Every spelling this merchant can be recognised by. */
    public val matchKeys: Set<String>
        get() = (listOf(matchKey) + aliases.map(::normaliseName)).filter { it.isNotBlank() }.toSet()

    /**
     * Wording for the interface. Keeply is careful here: a saved default is the
     * person's assumption, and mislabelling it as policy would be a lie that costs
     * someone a refund.
     */
    public fun returnRuleLabel(): String = when (defaultReturnPolicy) {
        is ReturnPolicy.Days -> "Your saved rule: ${defaultReturnPolicy.days} days"
        is ReturnPolicy.Until -> "Your saved rule: until ${defaultReturnPolicy.date}"
        ReturnPolicy.Unknown -> "No saved rule"
    }

    public companion object {
        public fun normaliseName(raw: String): String {
            val tokens = raw.lowercase(Locale.ROOT)
                .replace(NON_ALPHANUMERIC, " ")
                .split(' ')
                .filter { it.isNotBlank() && it !in NOISE_WORDS }

            // Till receipts carry branch numbers: "BEST BUY #1234" is the same shop as
            // "Best Buy". A leading number is part of the name, though, so "7 Eleven"
            // keeps its seven.
            var seenWord = false
            return tokens.filter { token ->
                val isNumber = token.all(Char::isDigit)
                val keep = !isNumber || !seenWord
                if (!isNumber) seenWord = true
                keep
            }.joinToString(" ")
        }

        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

        /** Corporate suffixes that appear on receipts but say nothing about which shop it is. */
        private val NOISE_WORDS = setOf(
            "inc", "incorporated", "llc", "ltd", "limited", "corp", "corporation",
            "co", "company", "gmbh", "sa", "sas", "bv", "nv", "plc", "store", "stores",
        )
    }
}

/** How confidently a piece of receipt text was matched to a saved merchant. */
public data class MerchantMatch(val merchant: Merchant, val confidence: FieldConfidence, val matchedText: String)
