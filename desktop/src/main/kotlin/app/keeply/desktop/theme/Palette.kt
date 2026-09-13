package app.keeply.desktop.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Keeply's colours.
 *
 * A warm paper ground rather than a cold grey one, because the thing this
 * application is about is paper, and because a receipt keeper that looks like a
 * dashboard feels like work. One accent, used sparingly, so that when something
 * is coloured it means something.
 */
internal object Palette {
    // Light: warm off-white paper, ink text, a deep green that reads as "kept".
    val paper = Color(0xFFFBF9F5)
    val card = Color(0xFFFFFFFF)
    val cardMuted = Color(0xFFF3F0EA)
    val ink = Color(0xFF1B1A17)
    val inkMuted = Color(0xFF6A655C)

    // Strengthened from 0xFFE6E1D8. A card sits on a page of nearly the same
    // luminance, so its border is the only thing separating them.
    val hairline = Color(0xFFDCD5C6)
    val green = Color(0xFF1F6B57)
    val greenSoft = Color(0xFFDCEDE6)

    // Darkened from 0xFF9A6212, which measured 4.34:1 on amberSoft and so
    // failed WCAG AA for the small text in a status pill. Now 5.24:1.
    val amber = Color(0xFF8A560E)
    val amberSoft = Color(0xFFFBEBD2)
    val red = Color(0xFF8F2E24)
    val redSoft = Color(0xFFF7E2DF)

    // Dark: not black. A dark warm grey keeps the paper feeling and is easier to
    // sit in front of for a while.
    val nightPaper = Color(0xFF161513)
    val nightCard = Color(0xFF201F1C)
    val nightCardMuted = Color(0xFF2A2825)
    val nightInk = Color(0xFFF2EFE9)
    val nightInkMuted = Color(0xFFA9A399)
    val nightHairline = Color(0xFF3D3932)
    val nightGreen = Color(0xFF7FC9B0)
    val nightGreenSoft = Color(0xFF1C3A32)
    val nightAmber = Color(0xFFE8B366)
    val nightAmberSoft = Color(0xFF3B2E19)
    val nightRed = Color(0xFFEBA79D)
    val nightRedSoft = Color(0xFF3B211D)
}

public val KeeplyLightColors = lightColorScheme(
    primary = Palette.green,
    onPrimary = Color.White,
    primaryContainer = Palette.greenSoft,
    onPrimaryContainer = Palette.green,
    secondary = Palette.amber,
    onSecondary = Color.White,
    secondaryContainer = Palette.amberSoft,
    onSecondaryContainer = Palette.amber,
    error = Palette.red,
    onError = Color.White,
    errorContainer = Palette.redSoft,
    onErrorContainer = Palette.red,
    background = Palette.paper,
    onBackground = Palette.ink,
    surface = Palette.card,
    onSurface = Palette.ink,
    surfaceVariant = Palette.cardMuted,
    onSurfaceVariant = Palette.inkMuted,
    outline = Palette.hairline,
    outlineVariant = Palette.hairline,
)

public val KeeplyDarkColors = darkColorScheme(
    primary = Palette.nightGreen,
    onPrimary = Color(0xFF10221D),
    primaryContainer = Palette.nightGreenSoft,
    onPrimaryContainer = Palette.nightGreen,
    secondary = Palette.nightAmber,
    onSecondary = Color(0xFF2A1E0B),
    secondaryContainer = Palette.nightAmberSoft,
    onSecondaryContainer = Palette.nightAmber,
    error = Palette.nightRed,
    onError = Color(0xFF2A100C),
    errorContainer = Palette.nightRedSoft,
    onErrorContainer = Palette.nightRed,
    background = Palette.nightPaper,
    onBackground = Palette.nightInk,
    surface = Palette.nightCard,
    onSurface = Palette.nightInk,
    surfaceVariant = Palette.nightCardMuted,
    onSurfaceVariant = Palette.nightInkMuted,
    outline = Palette.nightHairline,
    outlineVariant = Palette.nightHairline,
)
