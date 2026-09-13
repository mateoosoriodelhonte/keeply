package app.keeply.desktop.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing.
 *
 * Named rather than numeric, so a screen says what it means and every screen
 * breathes the same way. Keeply leans generous: the alternative to whitespace in
 * an application like this is a wall of small print, which is exactly what a
 * person is trying to get away from.
 */
public object Spacing {
    public val hairline: Dp = 1.dp
    public val tight: Dp = 4.dp
    public val small: Dp = 8.dp
    public val medium: Dp = 16.dp
    public val large: Dp = 24.dp
    public val section: Dp = 40.dp
    public val page: Dp = 32.dp

    /** Reading width. Long lines are hard to scan, however wide the window is. */
    public val maxContentWidth: Dp = 1100.dp
    public val maxProseWidth: Dp = 620.dp
}
