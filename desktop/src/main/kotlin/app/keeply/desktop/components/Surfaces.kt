package app.keeply.desktop.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keeply.desktop.theme.Spacing

/**
 * A plain card.
 *
 * Flat, with a hairline border rather than a shadow. Shadows everywhere make an
 * interface feel busy, and this is an application people open to find one thing.
 */
@Composable
public fun KeeplyCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Spacing.medium),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(padding)) { content() }
    }
}

/**
 * A section heading.
 *
 * Marked as a heading for screen readers, so somebody navigating by headings can
 * jump between "Needs your attention" and "Recent purchases" the way they expect.
 */
@Composable
public fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        action?.invoke()
    }
}

/**
 * What a screen says when there is nothing on it.
 *
 * Empty states are where an application either explains itself or makes somebody
 * feel they have done something wrong, so these read like a person wrote them.
 */
@Composable
public fun EmptyState(headline: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Spacing.maxProseWidth),
        )
        action?.let {
            Spacer(Modifier.height(Spacing.small))
            it()
        }
    }
}

/** A labelled fact, the shape most of Keeply's detail views are made of. */
@Composable
public fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = LABEL_WIDTH.dp, max = LABEL_WIDTH.dp),
        )
        Spacer(Modifier.widthIn(min = Spacing.medium))
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = value,
                style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            trailing?.invoke()
        }
    }
}

private const val LABEL_WIDTH = 170
