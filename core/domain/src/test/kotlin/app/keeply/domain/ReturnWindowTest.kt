package app.keeply.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReturnWindowTest {
    private val purchased = LocalDate.of(2026, 9, 12)

    @Test
    fun countsDaysFromThePurchaseDate() {
        val window = ReturnWindowCalculator.resolve(
            purchased,
            ReturnPolicy.Days(30),
            ReturnPolicySource.USER_ENTERED,
        )
        assertEquals(LocalDate.of(2026, 10, 12), window.deadline)
    }

    @Test
    fun handlesMonthEndsAndLeapDays() {
        val jan31 = ReturnWindowCalculator.resolve(
            LocalDate.of(2026, 1, 31),
            ReturnPolicy.Days(30),
            ReturnPolicySource.USER_ENTERED,
        )
        assertEquals(LocalDate.of(2026, 3, 2), jan31.deadline)

        val leap = ReturnWindowCalculator.resolve(
            LocalDate.of(2028, 2, 28),
            ReturnPolicy.Days(1),
            ReturnPolicySource.USER_ENTERED,
        )
        assertEquals(LocalDate.of(2028, 2, 29), leap.deadline)
    }

    @Test
    fun reportsStatusRelativeToAGivenDay() {
        val window = ReturnWindowCalculator.resolve(
            purchased,
            ReturnPolicy.Days(30),
            ReturnPolicySource.USER_ENTERED,
        )
        assertEquals(ReturnStatus.RETURNABLE, window.statusOn(LocalDate.of(2026, 9, 20)))
        assertEquals(ReturnStatus.ENDS_SOON, window.statusOn(LocalDate.of(2026, 10, 6)))
        assertEquals(ReturnStatus.ENDS_SOON, window.statusOn(LocalDate.of(2026, 10, 12)))
        assertEquals(ReturnStatus.ENDED, window.statusOn(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun countsDaysRemainingInclusively() {
        val window = ReturnWindowCalculator.resolve(
            purchased,
            ReturnPolicy.Days(30),
            ReturnPolicySource.USER_ENTERED,
        )
        assertEquals(30L, window.daysRemainingOn(purchased))
        assertEquals(1L, window.daysRemainingOn(LocalDate.of(2026, 10, 11)))
        assertEquals(0L, window.daysRemainingOn(LocalDate.of(2026, 10, 12)))
        assertEquals(-1L, window.daysRemainingOn(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun saysUnknownRatherThanGuessing() {
        val window = ReturnWindowCalculator.resolve(purchased, ReturnPolicy.Unknown, ReturnPolicySource.NONE)
        assertNull(window.deadline)
        assertEquals(ReturnStatus.UNKNOWN, window.statusOn(purchased))
        assertEquals(ReturnPolicySource.NONE, window.source)
    }

    @Test
    fun cannotProduceADeadlineWithoutAPurchaseDate() {
        val window = ReturnWindowCalculator.resolve(null, ReturnPolicy.Days(30), ReturnPolicySource.USER_ENTERED)
        assertNull(window.deadline)
        assertEquals(ReturnPolicySource.NONE, window.source)
    }

    @Test
    fun anExplicitDateDoesNotNeedAPurchaseDate() {
        val printed = LocalDate.of(2026, 10, 1)
        val window = ReturnWindowCalculator.resolve(
            null,
            ReturnPolicy.Until(printed),
            ReturnPolicySource.PRINTED_ON_RECEIPT,
        )
        assertEquals(printed, window.deadline)
        assertEquals(ReturnPolicySource.PRINTED_ON_RECEIPT, window.source)
    }

    @Test
    fun prefersEvidenceItCanPointAt() {
        val correction = ReturnPolicy.Days(45)
        val printed = ReturnPolicy.Until(LocalDate.of(2026, 10, 1))
        val saved = ReturnPolicy.Days(15)

        assertEquals(
            correction to ReturnPolicySource.USER_CORRECTION,
            ReturnWindowCalculator.choose(correction, printed, saved),
        )
        assertEquals(
            printed to ReturnPolicySource.PRINTED_ON_RECEIPT,
            ReturnWindowCalculator.choose(null, printed, saved),
        )
        assertEquals(
            saved to ReturnPolicySource.SAVED_MERCHANT_RULE,
            ReturnWindowCalculator.choose(null, null, saved),
        )
        assertEquals(
            ReturnPolicy.Unknown to ReturnPolicySource.NONE,
            ReturnWindowCalculator.choose(null, null, null),
        )
    }

    @Test
    fun everyDeadlineKeeplyShowsIsAttributable() {
        // Nothing may present a deadline as fact without saying where it came from.
        val window = ReturnWindowCalculator.resolve(
            purchased,
            ReturnPolicy.Days(15),
            ReturnPolicySource.SAVED_MERCHANT_RULE,
        )
        assertEquals(true, window.source.isUserAuthored)
    }

    @Test
    fun rejectsImplausibleWindows() {
        assertFailsWith<IllegalArgumentException> { ReturnPolicy.Days(-1) }
        assertFailsWith<IllegalArgumentException> { ReturnPolicy.Days(99_999) }
    }
}
