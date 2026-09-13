package app.keeply.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.desktop.components.EmptyState
import app.keeply.desktop.components.PurchaseCard
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseFilter
import app.keeply.domain.PurchaseSort
import app.keeply.domain.ReturnStatus
import app.keeply.domain.WarrantyStatus
import java.time.LocalDate

/**
 * Everything a person has saved.
 *
 * Search first, filters second. Most of the time somebody knows roughly what they
 * are looking for, and typing it is faster than choosing from a list of options.
 */
@Composable
public fun LibraryScreen(
    purchases: List<Purchase>,
    searchText: String,
    recognisedTerms: List<String>,
    filter: PurchaseFilter,
    today: LocalDate,
    onSearch: (String) -> Unit,
    onFilter: (PurchaseFilter) -> Unit,
    onClear: () -> Unit,
    onOpen: (Purchase) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(Spacing.page)) {
        Text(
            text = "All purchases",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.medium))

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearch,
            label = { Text("Search") },
            placeholder = { Text("headphones, Costco, returnable, under 100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (recognisedTerms.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.small))
            // Says what it did with the words it understood, so nothing about the
            // result is a mystery.
            Text(
                text = "Filtering by ${recognisedTerms.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            FilterChip(
                selected = ReturnStatus.RETURNABLE in filter.returnStatuses,
                onClick = {
                    onFilter(
                        filter.copy(
                            returnStatuses = if (ReturnStatus.RETURNABLE in filter.returnStatuses) {
                                emptySet()
                            } else {
                                setOf(ReturnStatus.RETURNABLE, ReturnStatus.ENDS_SOON)
                            },
                        ),
                    )
                },
                label = { Text("Still returnable") },
            )
            FilterChip(
                selected = WarrantyStatus.ACTIVE in filter.warrantyStatuses,
                onClick = {
                    onFilter(
                        filter.copy(
                            warrantyStatuses = if (WarrantyStatus.ACTIVE in filter.warrantyStatuses) {
                                emptySet()
                            } else {
                                setOf(WarrantyStatus.ACTIVE, WarrantyStatus.EXPIRING_SOON, WarrantyStatus.LIFETIME)
                            },
                        ),
                    )
                },
                label = { Text("Under warranty") },
            )
            FilterChip(
                selected = filter.onlyArchived,
                onClick = { onFilter(filter.copy(onlyArchived = !filter.onlyArchived, includeArchived = !filter.onlyArchived)) },
                label = { Text("Archived") },
            )
            AssistChip(
                onClick = {
                    onFilter(
                        filter.copy(
                            sort = if (filter.sort == PurchaseSort.RETURN_DEADLINE_SOONEST) {
                                PurchaseSort.NEWEST_FIRST
                            } else {
                                PurchaseSort.RETURN_DEADLINE_SOONEST
                            },
                        ),
                    )
                },
                label = {
                    Text(
                        if (filter.sort == PurchaseSort.RETURN_DEADLINE_SOONEST) {
                            "Sorted by deadline"
                        } else {
                            "Sorted by date"
                        },
                    )
                },
            )
            if (!filter.isEmpty || searchText.isNotBlank()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(Spacing.medium))
        if (purchases.isEmpty()) {
            EmptyState(
                headline = if (searchText.isBlank()) "No purchases yet" else "Nothing matched that",
                body = if (searchText.isBlank()) {
                    "Add a receipt and it will show up here."
                } else {
                    "Try fewer words, or clear the filters. Keeply searches product names, shops, " +
                        "your notes, and the text it read off the receipt."
                },
            )
        } else {
            Text(
                text = if (purchases.size == 1) "1 purchase" else "${purchases.size} purchases",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.small))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                items(purchases, key = { it.id.value }) { purchase ->
                    PurchaseCard(purchase, today, onOpen = { onOpen(purchase) })
                }
            }
        }
    }
}
