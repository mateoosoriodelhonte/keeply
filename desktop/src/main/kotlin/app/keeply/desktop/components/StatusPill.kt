package app.keeply.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.ReturnStatus
import app.keeply.domain.ReturnWindow
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyStatus
import java.time.LocalDate

/** How a status looks and, more importantly, what it says. */
public data class StatusAppearance(val label: String, val detail: String?, val tone: Tone) {
    public enum class Tone { GOOD, SOON, PAST, UNKNOWN }
}

/**
 * The small coloured label on a purchase card.
 *
 * Colour is never the only signal: every pill says its status in words as well,
 * because colour alone excludes anyone who cannot distinguish these two greens
 * and anyone glancing at a screen in sunlight.
 */
@Composable
public fun StatusPill(appearance: StatusAppearance, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val background: Color
    val foreground: Color
    when (appearance.tone) {
        StatusAppearance.Tone.GOOD -> {
            background = scheme.primaryContainer
            foreground = scheme.onPrimaryContainer
        }

        StatusAppearance.Tone.SOON -> {
            background = scheme.secondaryContainer
            foreground = scheme.onSecondaryContainer
        }

        StatusAppearance.Tone.PAST -> {
            background = scheme.surfaceVariant
            foreground = scheme.onSurfaceVariant
        }

        StatusAppearance.Tone.UNKNOWN -> {
            background = Color.Transparent
            foreground = scheme.onSurfaceVariant
        }
    }

    val description = listOfNotNull(appearance.label, appearance.detail).joinToString(", ")
    Text(
        text = appearance.detail?.let { "${appearance.label} · $it" } ?: appearance.label,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .semantics { contentDescription = description }
            .then(
                if (appearance.tone == StatusAppearance.Tone.UNKNOWN) {
                    Modifier.border(1.dp, scheme.outline, MaterialTheme.shapes.extraSmall)
                } else {
                    Modifier.background(background, MaterialTheme.shapes.extraSmall)
                },
            )
            .padding(horizontal = Spacing.small, vertical = Spacing.tight),
    )
}

/**
 * Turns a return window into something a person can read at a glance.
 *
 * "30 days left" rather than a date, because the question people actually have is
 * how long they have, not when the calendar says it ends. The date is on the
 * detail page for anyone who wants it.
 */
public fun ReturnWindow.appearance(today: LocalDate): StatusAppearance {
    val remaining = daysRemainingOn(today)
    return when (statusOn(today)) {
        ReturnStatus.RETURNABLE -> StatusAppearance(
            label = "Returnable",
            detail = remaining?.let { "$it days left" },
            tone = StatusAppearance.Tone.GOOD,
        )

        ReturnStatus.ENDS_SOON -> StatusAppearance(
            label = "Ends soon",
            detail = when (remaining) {
                0L -> "today"
                1L -> "tomorrow"
                else -> remaining?.let { "$it days left" }
            },
            tone = StatusAppearance.Tone.SOON,
        )

        ReturnStatus.ENDED -> StatusAppearance(
            label = "Return window ended",
            detail = deadline?.let { "on ${formatDate(it)}" },
            tone = StatusAppearance.Tone.PAST,
        )

        ReturnStatus.UNKNOWN -> StatusAppearance(
            label = "Return window not set",
            detail = null,
            tone = StatusAppearance.Tone.UNKNOWN,
        )
    }
}

public fun Warranty.appearance(today: LocalDate): StatusAppearance {
    val remaining = daysRemainingOn(today)
    return when (statusOn(today)) {
        WarrantyStatus.ACTIVE -> StatusAppearance(
            "Under warranty",
            endDate?.let { "until ${formatDate(it)}" },
            StatusAppearance.Tone.GOOD,
        )

        WarrantyStatus.EXPIRING_SOON -> StatusAppearance(
            "Warranty ending",
            when (remaining) {
                0L -> "today"
                1L -> "tomorrow"
                else -> remaining?.let { "in $it days" }
            },
            StatusAppearance.Tone.SOON,
        )

        WarrantyStatus.EXPIRED -> StatusAppearance(
            "Warranty expired",
            endDate?.let { "on ${formatDate(it)}" },
            StatusAppearance.Tone.PAST,
        )

        WarrantyStatus.LIFETIME -> StatusAppearance("Lifetime warranty", null, StatusAppearance.Tone.GOOD)

        WarrantyStatus.UNKNOWN -> StatusAppearance("Warranty not set", null, StatusAppearance.Tone.UNKNOWN)
    }
}

internal fun formatDate(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
