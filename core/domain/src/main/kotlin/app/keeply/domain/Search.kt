package app.keeply.domain

import java.time.LocalDate
import java.util.Locale

/** How the library is sorted. */
public enum class PurchaseSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
    PRICE_HIGH_TO_LOW,
    PRICE_LOW_TO_HIGH,
    RETURN_DEADLINE_SOONEST,
    NAME_A_TO_Z,
}

/** Structured filters the library and search both use. */
public data class PurchaseFilter(
    val merchantIds: Set<MerchantId> = emptySet(),
    val categoryIds: Set<CategoryId> = emptySet(),
    val tags: Set<String> = emptySet(),
    val purchasedOnOrAfter: LocalDate? = null,
    val purchasedOnOrBefore: LocalDate? = null,
    val minimumPriceMinor: Long? = null,
    val maximumPriceMinor: Long? = null,
    val returnStatuses: Set<ReturnStatus> = emptySet(),
    val warrantyStatuses: Set<WarrantyStatus> = emptySet(),
    val includeArchived: Boolean = false,
    val onlyArchived: Boolean = false,
    val sort: PurchaseSort = PurchaseSort.NEWEST_FIRST,
) {
    public val isEmpty: Boolean
        get() = merchantIds.isEmpty() && categoryIds.isEmpty() && tags.isEmpty() &&
            purchasedOnOrAfter == null && purchasedOnOrBefore == null &&
            minimumPriceMinor == null && maximumPriceMinor == null &&
            returnStatuses.isEmpty() && warrantyStatuses.isEmpty() &&
            !includeArchived && !onlyArchived
}

/**
 * A search someone typed, split into the parts Keeply can act on precisely and the
 * rest, which goes to full-text search.
 */
public data class SearchQuery(
    val raw: String,
    /** Words to match against product names, merchants, notes, tags and receipt text. */
    val text: String?,
    val filter: PurchaseFilter,
    /** The words that were understood as filters, so the interface can show what it did. */
    val recognisedTerms: List<String> = emptyList(),
) {
    public val isEmpty: Boolean get() = text.isNullOrBlank() && filter.isEmpty
}

/**
 * Turns a typed search into filters plus leftover words.
 *
 * This deliberately understands a small, fixed vocabulary rather than pretending to
 * understand language. "returnable", "warranty", "under 100" and a year are common
 * enough to be worth handling; everything else is passed through to full-text search,
 * which is usually what the person wanted anyway.
 */
public class SearchQueryParser(private val today: () -> LocalDate = LocalDate::now) {
    public fun parse(raw: String, base: PurchaseFilter = PurchaseFilter()): SearchQuery {
        val tokens = raw.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return SearchQuery(raw, null, base)

        var filter = base
        val leftover = mutableListOf<String>()
        val recognised = mutableListOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val word = token.lowercase(Locale.ROOT).trim('"', '\'', ',')
            val next = tokens.getOrNull(index + 1)?.lowercase(Locale.ROOT)

            val consumed = when {
                word in RETURNABLE_WORDS -> {
                    filter = filter.copy(returnStatuses = filter.returnStatuses + RETURN_OPEN)
                    recognised += token
                    1
                }

                word in RETURN_ENDING_WORDS || (word == "ends" && next == "soon") -> {
                    filter = filter.copy(returnStatuses = filter.returnStatuses + ReturnStatus.ENDS_SOON)
                    recognised += token
                    if (word == "ends" && next == "soon") 2 else 1
                }

                word in WARRANTY_WORDS -> {
                    filter = filter.copy(
                        warrantyStatuses = filter.warrantyStatuses +
                            setOf(WarrantyStatus.ACTIVE, WarrantyStatus.EXPIRING_SOON, WarrantyStatus.LIFETIME),
                    )
                    recognised += token
                    1
                }

                word in EXPIRING_WORDS -> {
                    filter = filter.copy(warrantyStatuses = filter.warrantyStatuses + WarrantyStatus.EXPIRING_SOON)
                    recognised += token
                    1
                }

                word == "archived" -> {
                    filter = filter.copy(onlyArchived = true, includeArchived = true)
                    recognised += token
                    1
                }

                word in UNDER_WORDS && next != null -> {
                    val minor = parseAmountMinor(next)
                    if (minor != null) {
                        filter = filter.copy(maximumPriceMinor = minor)
                        recognised += "$token $next"
                        2
                    } else {
                        0
                    }
                }

                word in OVER_WORDS && next != null -> {
                    val minor = parseAmountMinor(next)
                    if (minor != null) {
                        filter = filter.copy(minimumPriceMinor = minor)
                        recognised += "$token $next"
                        2
                    } else {
                        0
                    }
                }

                isYear(word) -> {
                    val year = word.toInt()
                    filter = filter.copy(
                        purchasedOnOrAfter = LocalDate.of(year, 1, 1),
                        purchasedOnOrBefore = LocalDate.of(year, 12, 31),
                    )
                    recognised += token
                    1
                }

                word == "this" && next == "month" -> {
                    val start = today().withDayOfMonth(1)
                    filter = filter.copy(
                        purchasedOnOrAfter = start,
                        purchasedOnOrBefore = start.plusMonths(1).minusDays(1),
                    )
                    recognised += "$token $next"
                    2
                }

                word == "this" && next == "year" -> {
                    val start = today().withDayOfYear(1)
                    filter = filter.copy(
                        purchasedOnOrAfter = start,
                        purchasedOnOrBefore = start.plusYears(1).minusDays(1),
                    )
                    recognised += "$token $next"
                    2
                }

                else -> 0
            }

            if (consumed == 0) {
                leftover += token
                index += 1
            } else {
                index += consumed
            }
        }

        val text = leftover.joinToString(" ").takeIf { it.isNotBlank() }
        return SearchQuery(raw, text, filter, recognised)
    }

    /** Reads a bare number in a search box as major units: "under 100" means 100.00. */
    private fun parseAmountMinor(token: String): Long? {
        val cleaned = token.trim('$', '€', '£', ',', '.')
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.movePointRight(2).toLong()
    }

    private fun isYear(word: String): Boolean = word.length == 4 && word.all(Char::isDigit) && word.toInt() in MIN_YEAR..MAX_YEAR

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MIN_YEAR = 1990
        const val MAX_YEAR = 2100

        val RETURN_OPEN = setOf(ReturnStatus.RETURNABLE, ReturnStatus.ENDS_SOON)
        val RETURNABLE_WORDS = setOf("returnable", "returns", "returning", "refundable")
        val RETURN_ENDING_WORDS = setOf("expiring", "closing", "soon")
        val WARRANTY_WORDS = setOf("warranty", "warranties", "guaranteed", "guarantee")
        val EXPIRING_WORDS = setOf("expires", "expiry", "expiration")
        val UNDER_WORDS = setOf("under", "below", "less")
        val OVER_WORDS = setOf("over", "above", "more")
    }
}
