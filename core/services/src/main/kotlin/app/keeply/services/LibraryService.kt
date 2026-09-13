package app.keeply.services

import app.keeply.data.KeeplyStore
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseFilter
import app.keeply.domain.PurchaseSort
import app.keeply.domain.SearchQuery
import app.keeply.domain.SearchQueryParser
import java.time.Clock
import java.time.LocalDate

/** What the library screen shows, and why it shows that. */
public data class LibraryResult(
    val purchases: List<Purchase>,
    val query: SearchQuery,
    /** Words Keeply understood as filters, so the interface can show what it did. */
    val recognisedTerms: List<String>,
    val totalBeforeFilters: Int,
)

/**
 * Finding things.
 *
 * Full-text search narrows first, because that is what SQLite is good at, and the
 * structured filters are then applied in Kotlin over the result. For a personal
 * library of a few thousand purchases this is comfortably fast, keeps the SQL
 * static and parameterised, and means the filter rules live next to the domain
 * types they are about rather than being spread across generated queries.
 */
public class LibraryService(private val store: KeeplyStore, private val clock: Clock = Clock.systemDefaultZone()) {
    private val parser = SearchQueryParser { LocalDate.now(clock) }

    public fun search(raw: String, base: PurchaseFilter = PurchaseFilter()): LibraryResult {
        val query = parser.parse(raw, base)
        val today = LocalDate.now(clock)

        val text = query.text
        val candidates = if (text.isNullOrBlank()) {
            allIncludingArchived(query.filter)
        } else {
            store.purchases.byIds(store.search.find(text))
        }

        val filtered = candidates.filter { it.matches(query.filter, today) }.sortedWith(comparatorFor(query.filter.sort))
        return LibraryResult(filtered, query, query.recognisedTerms, candidates.size)
    }

    public fun filter(filter: PurchaseFilter): LibraryResult = search("", filter)

    /** What needs attention today: closing return windows and expiring warranties. */
    public fun needsAttention(withinDays: Long = 30): List<Purchase> {
        val today = LocalDate.now(clock)
        val returns = store.purchases.returnsClosingBetween(today, today.plusDays(RETURN_HORIZON_DAYS))
        val warranties = store.purchases.warrantiesEndingBetween(today, today.plusDays(withinDays))
        return (returns + warranties)
            .distinctBy { it.id }
            .filter { it.needsAttentionOn(today) }
            .sortedBy { it.returnWindow.deadline ?: it.warranty.endDate }
    }

    public fun recent(limit: Int = RECENT_LIMIT): List<Purchase> = store.purchases.recent(limit)

    private fun allIncludingArchived(filter: PurchaseFilter): List<Purchase> = if (filter.includeArchived || filter.onlyArchived) {
        // selectAll hides archived purchases, so the archive view needs everything.
        store.purchases.byIds(store.purchases.fingerprints().mapNotNull { it.purchaseId })
    } else {
        store.purchases.all()
    }

    private fun comparatorFor(sort: PurchaseSort): Comparator<Purchase> = when (sort) {
        PurchaseSort.NEWEST_FIRST -> compareByDescending<Purchase> { it.purchaseDate }.thenByDescending { it.createdAt }
        PurchaseSort.OLDEST_FIRST -> compareBy<Purchase> { it.purchaseDate }.thenBy { it.createdAt }
        PurchaseSort.PRICE_HIGH_TO_LOW -> compareByDescending { it.price?.amountMinor ?: Long.MIN_VALUE }
        PurchaseSort.PRICE_LOW_TO_HIGH -> compareBy { it.price?.amountMinor ?: Long.MAX_VALUE }
        PurchaseSort.RETURN_DEADLINE_SOONEST -> compareBy { it.returnWindow.deadline ?: LocalDate.MAX }
        PurchaseSort.NAME_A_TO_Z -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.productName }
    }

    private companion object {
        const val RETURN_HORIZON_DAYS = 14L
        const val RECENT_LIMIT = 12
    }
}

/**
 * Whether a purchase belongs in a filtered view.
 *
 * Kept next to the domain types rather than expressed as SQL, so the rules about
 * return statuses and warranty statuses are written once and shared with the
 * screens that display them.
 */
internal fun Purchase.matches(filter: PurchaseFilter, today: LocalDate): Boolean {
    if (filter.onlyArchived && !isArchived) return false
    if (!filter.onlyArchived && !filter.includeArchived && isArchived) return false

    if (filter.merchantIds.isNotEmpty() && merchantId !in filter.merchantIds) return false
    if (filter.categoryIds.isNotEmpty() && categoryId !in filter.categoryIds) return false
    if (filter.tags.isNotEmpty() && tags.none { it in filter.tags }) return false

    val bought = purchaseDate
    filter.purchasedOnOrAfter?.let { if (bought == null || bought < it) return false }
    filter.purchasedOnOrBefore?.let { if (bought == null || bought > it) return false }

    filter.minimumPriceMinor?.let { if ((price?.amountMinor ?: return false) < it) return false }
    filter.maximumPriceMinor?.let { if ((price?.amountMinor ?: return false) > it) return false }

    if (filter.returnStatuses.isNotEmpty() && returnStatusOn(today) !in filter.returnStatuses) return false
    if (filter.warrantyStatuses.isNotEmpty() && warrantyStatusOn(today) !in filter.warrantyStatuses) return false
    return true
}
