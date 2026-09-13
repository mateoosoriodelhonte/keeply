package app.keeply.services

import app.keeply.data.KeeplyStore
import java.time.Clock
import java.time.LocalDate

/** A handful of facts about a person's own library. Not advice. */
public data class Insights(
    val purchasesThisMonth: Long,
    val activeReturnWindows: Int,
    val returnsClosingThisWeek: Int,
    val warrantiesExpiringThisMonth: Int,
    val topMerchants: List<Pair<String, Long>>,
    val purchasesByCategory: List<Pair<String, Long>>,
    val totalPurchases: Long,
)

/**
 * Counts things a person might want to know about their own library.
 *
 * Deliberately plain. Keeply keeps receipts; it is not a budgeting application,
 * and turning a list of purchases into spending advice would be both a different
 * product and one that needs to be much more careful about what it claims.
 */
public class InsightsService(private val store: KeeplyStore, private val clock: Clock = Clock.systemDefaultZone()) {
    public fun summarise(): Insights {
        val today = LocalDate.now(clock)
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)

        val categoriesById = store.categories.all().associateBy { it.id.value }
        val closingThisWeek = store.purchases.returnsClosingBetween(today, today.plusDays(DAYS_IN_WEEK))
        val allOpenReturns = store.purchases.returnsClosingBetween(today, today.plusYears(1))
        val expiringThisMonth = store.purchases.warrantiesEndingBetween(today, monthEnd)

        return Insights(
            purchasesThisMonth = store.purchases.countPurchasedBetween(monthStart, monthEnd),
            activeReturnWindows = allOpenReturns.size,
            returnsClosingThisWeek = closingThisWeek.size,
            warrantiesExpiringThisMonth = expiringThisMonth.size,
            topMerchants = store.purchases.topMerchants(TOP_MERCHANTS),
            purchasesByCategory = store.purchases.countByCategory()
                .mapNotNull { (id, count) ->
                    val name = id?.let { categoriesById[it]?.name } ?: UNCATEGORISED
                    name to count
                }
                .sortedByDescending { it.second },
            totalPurchases = store.purchases.count(),
        )
    }

    private companion object {
        const val DAYS_IN_WEEK = 7L
        const val TOP_MERCHANTS = 5
        const val UNCATEGORISED = "Uncategorised"
    }
}
