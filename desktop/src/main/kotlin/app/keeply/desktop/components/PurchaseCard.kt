package app.keeply.desktop.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.Purchase
import java.time.LocalDate
import java.util.Locale

/**
 * One purchase, as it appears in a list.
 *
 * What it shows is what somebody would want if they could only see three things:
 * what it was, what it cost, and whether they can still take it back.
 */
@Composable
public fun PurchaseCard(purchase: Purchase, today: LocalDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val returns = purchase.returnWindow.appearance(today)
    val warranty = purchase.warranty.appearance(today)
    val price = purchase.price?.format(Locale.getDefault())

    // One description for the whole card, so a screen reader announces a purchase
    // as a sentence rather than as five unrelated fragments.
    val spoken = buildString {
        append(purchase.productName)
        purchase.merchantName?.let { append(", from $it") }
        price?.let { append(", $it") }
        purchase.purchaseDate?.let { append(", bought ${formatDate(it)}") }
        append(". ${returns.label}")
        returns.detail?.let { append(", $it") }
    }

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.medium)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = purchase.productName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        purchase.merchantName,
                        purchase.purchaseDate?.let(::formatDate),
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                price?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Wraps rather than squeezing: a narrow window should stack the pills,
            // not compress one of them into a column of single characters.
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                StatusPill(returns)
                if (warranty.tone != StatusAppearance.Tone.UNKNOWN) StatusPill(warranty)
            }
        }
    }
}
