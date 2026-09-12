package app.keeply.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WarrantyTest {
    private val bought = LocalDate.of(2026, 9, 12)

    @Test
    fun countsMonthsFromWhenCoverStarts() {
        val warranty = WarrantyCalculator.resolve(bought, WarrantyTerm.Months(12), WarrantyProvenance.DOCUMENTED)
        assertEquals(LocalDate.of(2027, 9, 12), warranty.endDate)
        assertEquals(365L, warranty.daysRemainingOn(bought))
    }

    @Test
    fun clampsToTheEndOfAShorterMonth() {
        val warranty = WarrantyCalculator.resolve(
            LocalDate.of(2026, 8, 31),
            WarrantyTerm.Months(6),
            WarrantyProvenance.USER_ENTERED,
        )
        assertEquals(LocalDate.of(2027, 2, 28), warranty.endDate)
    }

    @Test
    fun reportsStatusRelativeToAGivenDay() {
        val warranty = WarrantyCalculator.resolve(bought, WarrantyTerm.Months(12), WarrantyProvenance.DOCUMENTED)
        assertEquals(WarrantyStatus.ACTIVE, warranty.statusOn(LocalDate.of(2027, 1, 1)))
        assertEquals(WarrantyStatus.EXPIRING_SOON, warranty.statusOn(LocalDate.of(2027, 8, 20)))
        assertEquals(WarrantyStatus.EXPIRED, warranty.statusOn(LocalDate.of(2027, 9, 13)))
    }

    @Test
    fun lifetimeNeverExpires() {
        val warranty = WarrantyCalculator.resolve(bought, WarrantyTerm.Lifetime, WarrantyProvenance.DOCUMENTED)
        assertNull(warranty.endDate)
        assertEquals(WarrantyStatus.LIFETIME, warranty.statusOn(LocalDate.of(2099, 1, 1)))
    }

    @Test
    fun unknownStaysUnknown() {
        val warranty = Warranty.unknown
        assertEquals(WarrantyStatus.UNKNOWN, warranty.statusOn(bought))
        assertNull(warranty.daysRemainingOn(bought))
    }

    @Test
    fun keepsTrackOfWhoSaidSo() {
        // A warranty read off a document and one someone half-remembers are different
        // claims, and Keeply has to be able to tell a person which they are looking at.
        val documented = WarrantyCalculator.resolve(bought, WarrantyTerm.Months(24), WarrantyProvenance.DOCUMENTED)
        val suggested = WarrantyCalculator.resolve(bought, WarrantyTerm.Months(24), WarrantyProvenance.SUGGESTED)
        assertEquals(documented.endDate, suggested.endDate)
        assertEquals(WarrantyProvenance.DOCUMENTED, documented.provenance)
        assertEquals(WarrantyProvenance.SUGGESTED, suggested.provenance)
    }

    @Test
    fun cannotComputeAnEndWithoutAStart() {
        val warranty = WarrantyCalculator.resolve(null, WarrantyTerm.Months(12), WarrantyProvenance.USER_ENTERED)
        assertNull(warranty.endDate)
        assertEquals(WarrantyStatus.UNKNOWN, warranty.statusOn(bought))
    }

    @Test
    fun anExplicitEndDateStandsAlone() {
        val end = LocalDate.of(2030, 1, 1)
        val warranty = WarrantyCalculator.resolve(null, WarrantyTerm.Until(end), WarrantyProvenance.DOCUMENTED)
        assertEquals(end, warranty.endDate)
    }

    @Test
    fun rejectsImplausibleTerms() {
        assertFailsWith<IllegalArgumentException> { WarrantyTerm.Months(0) }
        assertFailsWith<IllegalArgumentException> { WarrantyTerm.Months(5_000) }
        assertFailsWith<IllegalArgumentException> { WarrantyTerm.Days(0) }
    }
}
