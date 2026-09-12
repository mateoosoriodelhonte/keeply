package app.keeply.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchQueryParserTest {
    private val parser = SearchQueryParser { LocalDate.of(2026, 9, 12) }

    @Test
    fun plainWordsGoToFullTextSearch() {
        val query = parser.parse("headphones")
        assertEquals("headphones", query.text)
        assertTrue(query.filter.isEmpty)
    }

    @Test
    fun recognisesReturnAndWarrantyWords() {
        val returnable = parser.parse("returnable")
        assertEquals(setOf(ReturnStatus.RETURNABLE, ReturnStatus.ENDS_SOON), returnable.filter.returnStatuses)
        assertEquals(null, returnable.text)

        val warranty = parser.parse("warranty")
        assertTrue(WarrantyStatus.ACTIVE in warranty.filter.warrantyStatuses)
    }

    @Test
    fun combinesAShopNameWithAFilter() {
        val query = parser.parse("Costco returnable")
        assertEquals("Costco", query.text)
        assertEquals(setOf(ReturnStatus.RETURNABLE, ReturnStatus.ENDS_SOON), query.filter.returnStatuses)
        assertEquals(listOf("returnable"), query.recognisedTerms)
    }

    @Test
    fun readsAYearAsADateRange() {
        val query = parser.parse("2026")
        assertEquals(LocalDate.of(2026, 1, 1), query.filter.purchasedOnOrAfter)
        assertEquals(LocalDate.of(2026, 12, 31), query.filter.purchasedOnOrBefore)
    }

    @Test
    fun readsPriceBounds() {
        val under = parser.parse("under 100")
        assertEquals(10_000L, under.filter.maximumPriceMinor)
        assertEquals(null, under.text)

        val over = parser.parse("over 49.99")
        assertEquals(4_999L, over.filter.minimumPriceMinor)
    }

    @Test
    fun readsRelativeDateRanges() {
        val thisMonth = parser.parse("this month")
        assertEquals(LocalDate.of(2026, 9, 1), thisMonth.filter.purchasedOnOrAfter)
        assertEquals(LocalDate.of(2026, 9, 30), thisMonth.filter.purchasedOnOrBefore)

        val thisYear = parser.parse("this year")
        assertEquals(LocalDate.of(2026, 1, 1), thisYear.filter.purchasedOnOrAfter)
        assertEquals(LocalDate.of(2026, 12, 31), thisYear.filter.purchasedOnOrBefore)
    }

    @Test
    fun leavesUnrecognisedWordsAloneRatherThanGuessing() {
        // "under" without a number is just a word someone typed.
        val query = parser.parse("under the desk")
        assertEquals("under the desk", query.text)
        assertEquals(null, query.filter.maximumPriceMinor)
    }

    @Test
    fun findsArchivedItemsOnlyWhenAsked() {
        val normal = parser.parse("blender")
        assertEquals(false, normal.filter.includeArchived)

        val archived = parser.parse("archived blender")
        assertTrue(archived.filter.onlyArchived)
        assertEquals("blender", archived.text)
    }

    @Test
    fun handlesSeveralFiltersAtOnce() {
        val query = parser.parse("electronics returnable under 500 2026")
        assertEquals("electronics", query.text)
        assertEquals(50_000L, query.filter.maximumPriceMinor)
        assertEquals(LocalDate.of(2026, 1, 1), query.filter.purchasedOnOrAfter)
        assertTrue(ReturnStatus.RETURNABLE in query.filter.returnStatuses)
    }

    @Test
    fun anEmptySearchFiltersNothing() {
        val query = parser.parse("   ")
        assertTrue(query.isEmpty)
    }
}
