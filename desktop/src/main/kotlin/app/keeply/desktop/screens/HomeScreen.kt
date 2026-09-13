package app.keeply.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.desktop.components.EmptyState
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.components.PurchaseCard
import app.keeply.desktop.components.SectionHeader
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.Purchase
import app.keeply.services.Insights
import java.time.LocalDate

/**
 * The home screen answers one question: what needs my attention?
 *
 * Everything closing soon is above everything else, because that is the only
 * thing on this screen with a deadline attached. Recent purchases come next, so
 * the answer to "did I remember to add that?" is visible without searching.
 */
@Composable
public fun HomeScreen(
    needsAttention: List<Purchase>,
    recent: List<Purchase>,
    insights: Insights?,
    today: LocalDate,
    onOpen: (Purchase) -> Unit,
    onAddReceipt: () -> Unit,
    onSeeAll: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Keeply",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Button(onClick = onAddReceipt) { Text("Add a receipt") }
        }

        if (needsAttention.isEmpty() && recent.isEmpty()) {
            EmptyState(
                headline = "Nothing here yet",
                body = "Drop a photo of a receipt anywhere on this window, or use Add a receipt. " +
                    "Keeply will read it and show you what it found before saving anything.",
                action = { TextButton(onClick = onTryDemo) { Text("Show me an example") } },
            )
            return@Column
        }

        if (needsAttention.isNotEmpty()) {
            Column {
                SectionHeader("Needs your attention")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    needsAttention.take(ATTENTION_LIMIT).forEach { purchase ->
                        PurchaseCard(purchase, today, onOpen = { onOpen(purchase) })
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            Column {
                SectionHeader(
                    title = "Recent purchases",
                    action = { TextButton(onClick = onSeeAll) { Text("See all") } },
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    recent.take(RECENT_LIMIT).forEach { purchase ->
                        PurchaseCard(purchase, today, onOpen = { onOpen(purchase) })
                    }
                }
            }
        }

        insights?.let { Summary(it) }
    }
}

/**
 * A few counts about a person's own library.
 *
 * Deliberately plain, and deliberately not money advice: Keeply keeps receipts.
 */
@Composable
private fun Summary(insights: Insights) {
    KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
        Text(
            text = "Your library",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.small))
        listOf(
            "Purchases saved" to insights.totalPurchases.toString(),
            "Added this month" to insights.purchasesThisMonth.toString(),
            "Return windows still open" to insights.activeReturnWindows.toString(),
            "Warranties ending this month" to insights.warrantiesExpiringThisMonth.toString(),
        ).forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.tight),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (insights.topMerchants.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "Most often: " + insights.topMerchants.joinToString(", ") { it.first },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val ATTENTION_LIMIT = 6
private const val RECENT_LIMIT = 8
