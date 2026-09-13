package app.keeply.desktop

import app.keeply.desktop.components.StatusAppearance
import app.keeply.desktop.components.appearance
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindowCalculator
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyCalculator
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The words Keeply puts on a status.
 *
 * Worth testing on their own, because these are the sentences a person makes a
 * decision from. "Ends soon, tomorrow" and "Return window ended" have to be right
 * on the day they matter, and colour can never be the only thing carrying them.
 */
class StatusWordingTest {
    private val today = LocalDate.of(2026, 9, 12)

    private fun returnWindow(days: Int, boughtDaysAgo: Long) = ReturnWindowCalculator.resolve(
        today.minusDays(boughtDaysAgo),
        ReturnPolicy.Days(days),
        ReturnPolicySource.USER_ENTERED,
    )

    @Test
    fun saysHowLongIsLeftRatherThanJustADate() {
        // The question is "how long have I got", not "what is the date".
        val plenty = returnWindow(days = 30, boughtDaysAgo = 2).appearance(today)
        assertEquals("Returnable", plenty.label)
        assertEquals("28 days left", plenty.detail)
        assertEquals(StatusAppearance.Tone.GOOD, plenty.tone)
    }

    @Test
    fun usesTheWordsPeopleUseForTodayAndTomorrow() {
        assertEquals("tomorrow", returnWindow(days = 30, boughtDaysAgo = 29).appearance(today).detail)
        assertEquals("today", returnWindow(days = 30, boughtDaysAgo = 30).appearance(today).detail)
    }

    @Test
    fun aClosedWindowSaysSoPlainlyAndIsNotAlarming() {
        val ended = returnWindow(days = 30, boughtDaysAgo = 40).appearance(today)
        assertEquals("Return window ended", ended.label)
        // Past, not an error: somebody who cannot return something has not done
        // anything wrong.
        assertEquals(StatusAppearance.Tone.PAST, ended.tone)
    }

    @Test
    fun anUnknownWindowSaysItIsNotSetRatherThanGuessing() {
        val unknown = ReturnWindowCalculator.resolve(today, ReturnPolicy.Unknown, ReturnPolicySource.NONE)
            .appearance(today)
        assertEquals("Return window not set", unknown.label)
        assertEquals(StatusAppearance.Tone.UNKNOWN, unknown.tone)
    }

    @Test
    fun everyStatusCarriesItsMeaningInWords() {
        // Colour is never the only signal. Anyone who cannot tell these two greens
        // apart, or is looking at a screen in sunlight, still gets the answer.
        val all = listOf(
            returnWindow(30, 2).appearance(today),
            returnWindow(30, 29).appearance(today),
            returnWindow(30, 40).appearance(today),
            ReturnWindowCalculator.resolve(today, ReturnPolicy.Unknown, ReturnPolicySource.NONE).appearance(today),
            WarrantyCalculator.resolve(today.minusMonths(1), WarrantyTerm.Months(24), WarrantyProvenance.DOCUMENTED)
                .appearance(today),
            Warranty.unknown.appearance(today),
        )
        all.forEach { appearance ->
            assertTrue(appearance.label.isNotBlank(), "a status with no words is only a colour")
            assertTrue(appearance.label.first().isUpperCase())
        }
        assertEquals(all.size, all.map { it.label }.toSet().size, "each status needs its own wording")
    }

    @Test
    fun aLifetimeWarrantySaysSoRatherThanShowingNoDate() {
        val lifetime = WarrantyCalculator.resolve(today, WarrantyTerm.Lifetime, WarrantyProvenance.DOCUMENTED)
            .appearance(today)
        assertEquals("Lifetime warranty", lifetime.label)
        assertEquals(StatusAppearance.Tone.GOOD, lifetime.tone)
    }

    @Test
    fun aWarrantyEndingSoonSaysWhen() {
        val ending = WarrantyCalculator.resolve(
            today.minusMonths(12).plusDays(18),
            WarrantyTerm.Months(12),
            WarrantyProvenance.DOCUMENTED,
        ).appearance(today)
        assertEquals("Warranty ending", ending.label)
        assertEquals("in 18 days", assertNotNull(ending.detail))
    }
}
