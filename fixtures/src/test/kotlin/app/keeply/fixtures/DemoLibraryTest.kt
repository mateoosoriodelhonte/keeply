package app.keeply.fixtures

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoLibraryTest {
    private val today = LocalDate.of(2026, 9, 12)

    @Test
    fun alwaysShowsSomethingAboutToNeedAttention() {
        // Someone opening the demo should see what the home screen is for, not a
        // screen of things that expired last year.
        val demo = DemoLibrary.purchases(today)

        val closingSoon = demo.filter { purchase ->
            val days = (purchase.returnPolicy as? app.keeply.domain.ReturnPolicy.Days)?.days ?: return@filter false
            val deadline = purchase.receipt.purchaseDate.plusDays(days.toLong())
            val remaining = ChronoUnit.DAYS.between(today, deadline)
            remaining in 0..7
        }
        assertTrue(closingSoon.isNotEmpty(), "expected at least one return window closing within a week")
    }

    @Test
    fun includesAWarrantyAboutToExpire() {
        val demo = DemoLibrary.purchases(today)
        val expiring = demo.filter { purchase ->
            val months = (purchase.warranty as? app.keeply.domain.WarrantyTerm.Months)?.months
                ?: return@filter false
            val end = purchase.receipt.purchaseDate.plusMonths(months.toLong())
            ChronoUnit.DAYS.between(today, end) in 0..30
        }
        assertTrue(expiring.isNotEmpty(), "expected at least one warranty expiring within a month")
    }

    @Test
    fun everyDemoPurchaseHasAReceiptThatAddsUp() {
        DemoLibrary.purchases(today).forEach { purchase ->
            assertEquals(
                purchase.receipt.totalMinor,
                purchase.receipt.subtotalMinor + purchase.receipt.taxMinor,
            )
            assertTrue(purchase.receipt.items.isNotEmpty())
        }
    }

    @Test
    fun movesWithTheCalendarRatherThanBeingPinnedToADate() {
        val early = DemoLibrary.purchases(LocalDate.of(2026, 1, 1)).first()
        val later = DemoLibrary.purchases(LocalDate.of(2027, 6, 1)).first()
        assertTrue(later.receipt.purchaseDate > early.receipt.purchaseDate)
    }

    @Test
    fun coversARangeOfCategories() {
        val categories = DemoLibrary.purchases(today).map { it.categoryId }.toSet()
        assertTrue(categories.size >= 4, "demo data should not all be one category")
    }
}
