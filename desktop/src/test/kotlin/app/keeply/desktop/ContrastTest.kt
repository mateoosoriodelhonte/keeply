package app.keeply.desktop

import androidx.compose.ui.graphics.Color
import app.keeply.desktop.theme.KeeplyDarkColors
import app.keeply.desktop.theme.KeeplyLightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every colour pair Keeply actually puts text on, measured against WCAG AA.
 *
 * Contrast is easy to get wrong by eye and easy to check by arithmetic, and a
 * palette drifts one tweak at a time. This caught a real failure: the "ends soon"
 * status pill measured 4.34:1, which is below the 4.5:1 that small text needs, and
 * that pill is the one carrying the most urgent thing Keeply ever has to say.
 */
class ContrastTest {

    /** WCAG 2.1: 4.5:1 for body text, 3:1 for large text. Keeply's labels are small. */
    private val minimumForText = 4.5
    private val minimumForLargeText = 3.0

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun check(name: String, foreground: Color, background: Color, minimum: Double = minimumForText) {
        val measured = ratio(foreground, background)
        assertTrue(
            measured >= minimum,
            "$name measured %.2f:1, below the %.1f:1 it needs".format(measured, minimum),
        )
    }

    @Test
    fun everyLightPairingIsReadable() {
        with(KeeplyLightColors) {
            check("body text on the page", onBackground, background)
            check("body text on a card", onSurface, surface)
            check("secondary text on the page", onSurfaceVariant, background)
            check("secondary text on a card", onSurfaceVariant, surface)
            check("secondary text on a muted panel", onSurfaceVariant, surfaceVariant)
            check("a primary button's label", onPrimary, primary)
            check("the returnable pill", onPrimaryContainer, primaryContainer)
            check("the ends-soon pill", onSecondaryContainer, secondaryContainer)
            check("an error message", onErrorContainer, errorContainer)
            check("a link or accent on the page", primary, background)
        }
    }

    @Test
    fun everyDarkPairingIsReadable() {
        with(KeeplyDarkColors) {
            check("body text on the page", onBackground, background)
            check("body text on a card", onSurface, surface)
            check("secondary text on a card", onSurfaceVariant, surface)
            check("secondary text on a muted panel", onSurfaceVariant, surfaceVariant)
            check("a primary button's label", onPrimary, primary)
            check("the returnable pill", onPrimaryContainer, primaryContainer)
            check("the ends-soon pill", onSecondaryContainer, secondaryContainer)
            check("an error message", onErrorContainer, errorContainer)
            check("a link or accent on the page", primary, background)
        }
    }

    @Test
    fun theFocusIndicatorIsStrongEnoughToFind() {
        // This one is covered by WCAG 1.4.11 and it matters: somebody navigating by
        // keyboard has nothing else telling them where they are. Material draws the
        // focus ring in the primary colour.
        with(KeeplyLightColors) { check("the focus ring on the page", primary, background, minimumForLargeText) }
        with(KeeplyLightColors) { check("the focus ring on a card", primary, surface, minimumForLargeText) }
        with(KeeplyDarkColors) { check("the focus ring on the page", primary, background, minimumForLargeText) }
        with(KeeplyDarkColors) { check("the focus ring on a card", primary, surface, minimumForLargeText) }
    }

    @Test
    fun aCardIsVisiblySeparateFromThePageBehindIt() {
        // Not the 3:1 rule: that covers controls and meaningful graphics, and a card
        // is a grouping container whose contents carry the meaning and already meet
        // AA. It still has to be *seen*, though, because a card on a page of almost
        // identical luminance is separated by its border and nothing else.
        with(KeeplyLightColors) {
            val measured = ratio(outline, surface)
            assertTrue(measured >= 1.4, "a card border measured %.2f:1 and disappears".format(measured))
        }
        with(KeeplyDarkColors) {
            val measured = ratio(outline, surface)
            assertTrue(measured >= 1.2, "a card border measured %.2f:1 and disappears".format(measured))
        }
    }

    @Test
    fun bothThemesUseTheSameStructure() {
        // A pairing that exists in one theme and not the other is how a dark mode
        // ends up with unreadable text that nobody notices for months.
        assertTrue(KeeplyLightColors.primary != KeeplyDarkColors.primary)
        assertTrue(relativeLuminance(KeeplyLightColors.background) > 0.5, "the light theme should be light")
        assertTrue(relativeLuminance(KeeplyDarkColors.background) < 0.1, "the dark theme should be dark")
    }
}
