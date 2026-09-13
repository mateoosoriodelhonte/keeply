package app.keeply.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How much Keeply is allowed to move.
 *
 * Somebody who has asked their system for less motion has asked for a reason.
 * Every animation in the application reads this rather than deciding for itself.
 */
public data class MotionPreference(val reduced: Boolean = false)

public val LocalMotion: androidx.compose.runtime.ProvidableCompositionLocal<MotionPreference> =
    staticCompositionLocalOf { MotionPreference() }

/** Text scale, so someone who needs larger text gets it everywhere at once. */
public val LocalTextScale: androidx.compose.runtime.ProvidableCompositionLocal<Float> =
    staticCompositionLocalOf { 1f }

/**
 * Typography.
 *
 * The system font, because a receipt keeper should look like it belongs on the
 * machine rather than like a web page. The scale is generous: this is an
 * application people open to read a number and close again, so the number should
 * be large enough to read from wherever they are sitting.
 */
private fun typography(scale: Float): Typography {
    fun size(value: Int) = (value * scale).sp
    fun lineHeight(value: Int) = (value * scale).sp

    return Typography(
        displaySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(34),
            lineHeight = lineHeight(40),
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(26),
            lineHeight = lineHeight(32),
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(21),
            lineHeight = lineHeight(28),
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(18),
            lineHeight = lineHeight(24),
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = size(15),
            lineHeight = lineHeight(20),
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = size(15),
            lineHeight = lineHeight(22),
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = size(14),
            lineHeight = lineHeight(20),
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = size(13),
            lineHeight = lineHeight(18),
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = size(12),
            lineHeight = lineHeight(16),
            letterSpacing = 0.2.sp,
        ),
    )
}

private val KeeplyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
public fun KeeplyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMotion provides MotionPreference(reducedMotion),
        LocalTextScale provides textScale,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) KeeplyDarkColors else KeeplyLightColors,
            typography = typography(textScale),
            shapes = KeeplyShapes,
            content = content,
        )
    }
}
