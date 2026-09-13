package app.keeply.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.keeply.desktop.components.DetailRow
import app.keeply.desktop.components.DocumentImage
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.components.StatusPill
import app.keeply.desktop.components.appearance
import app.keeply.desktop.components.formatDate
import app.keeply.desktop.components.rememberDocumentImage
import app.keeply.desktop.theme.Spacing
import app.keeply.documents.DataDirectory
import app.keeply.domain.DocumentText
import app.keeply.domain.FieldSource
import app.keeply.domain.Purchase
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.StoredDocument
import app.keeply.domain.WarrantyProvenance
import java.time.LocalDate
import java.util.Locale

/**
 * One purchase, in full.
 *
 * The original receipt is always one click away, because the reason somebody
 * saved a purchase is usually that they will need to prove it. Where a value came
 * from is shown next to it: a deadline from a rule the person saved reads
 * differently from one printed on the receipt, and pretending otherwise could
 * cost them a refund.
 */
@Composable
public fun DetailScreen(
    purchase: Purchase,
    receipt: StoredDocument?,
    receiptText: DocumentText?,
    manuals: List<StoredDocument>,
    directory: DataDirectory,
    today: LocalDate,
    onOpenDocument: (StoredDocument) -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        TextButton(onClick = onBack) { Text("Back") }

        Text(
            text = purchase.productName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        purchase.merchantName?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            StatusPill(purchase.returnWindow.appearance(today))
            StatusPill(purchase.warranty.appearance(today))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Column(Modifier.weight(1f)) {
                KeeplyCard {
                    purchase.price?.let { DetailRow("Paid", it.format(Locale.getDefault()), emphasis = true) }
                    purchase.purchaseDate?.let { DetailRow("Purchased", formatDate(it)) }
                    purchase.tax?.let { DetailRow("Tax", it.format(Locale.getDefault())) }
                    purchase.receiptNumber?.let { DetailRow("Receipt number", it) }
                    purchase.serialNumber?.let { DetailRow("Serial number", it) }
                    purchase.paymentMethodLabel?.let { DetailRow("Paid with", it) }
                }

                Spacer(Modifier.height(Spacing.medium))
                KeeplyCard {
                    Text(
                        "Returns",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.small))
                    DetailRow(
                        label = "Return deadline",
                        value = purchase.returnWindow.deadline?.let(::formatDate) ?: "Not set",
                        trailing = {
                            Text(
                                text = sourceWording(purchase.returnWindow.source),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    val remaining = purchase.returnWindow.daysRemainingOn(today)
                    if (remaining != null && remaining >= 0) {
                        DetailRow("Time left", if (remaining == 0L) "Today is the last day" else "$remaining days")
                    }
                }

                Spacer(Modifier.height(Spacing.medium))
                KeeplyCard {
                    Text(
                        "Warranty",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.small))
                    DetailRow(
                        label = "Warranty ends",
                        value = purchase.warranty.endDate?.let(::formatDate)
                            ?: if (purchase.warranty.term == app.keeply.domain.WarrantyTerm.Lifetime) {
                                "Never, it is a lifetime warranty"
                            } else {
                                "Not set"
                            },
                        trailing = {
                            Text(
                                text = warrantyWording(purchase.warranty.provenance),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    purchase.warranty.notes?.let { DetailRow("Notes", it) }
                }

                if (purchase.notes != null || purchase.tags.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.medium))
                    KeeplyCard {
                        purchase.notes?.let { DetailRow("Notes", it) }
                        if (purchase.tags.isNotEmpty()) DetailRow("Tags", purchase.tags.joinToString(", "))
                    }
                }

                if (purchase.lineItems.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.medium))
                    KeeplyCard {
                        Text(
                            "On the receipt",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(Spacing.small))
                        purchase.lineItems.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Spacing.tight),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.quantity?.takeIf { it > 1 }?.let { "$it × ${item.description}" }
                                        ?: item.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                item.totalPrice?.let {
                                    Text(it.format(Locale.getDefault()), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    OutlinedButton(onClick = onArchive) {
                        Text(if (purchase.isArchived) "Bring back" else "Archive")
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                KeeplyCard {
                    Text(
                        "The receipt",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.small))
                    if (receipt == null) {
                        Text(
                            "No receipt is attached to this purchase.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().heightIn(max = PREVIEW_HEIGHT.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (val image = rememberDocumentImage(directory, receipt, maxWidth = PREVIEW_WIDTH)) {
                                DocumentImage.Loading -> CircularProgressIndicator()

                                is DocumentImage.Failed -> Text(image.message, style = MaterialTheme.typography.bodyMedium)

                                is DocumentImage.Ready -> Image(
                                    bitmap = image.bitmap,
                                    contentDescription = "The receipt for ${purchase.productName}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.small))
                        Button(onClick = { onOpenDocument(receipt) }) { Text("Open the receipt") }
                        receiptText?.let { text ->
                            Spacer(Modifier.height(Spacing.small))
                            Text(
                                text = readingWording(text),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (manuals.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.medium))
                    KeeplyCard {
                        Text(
                            "Manuals",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(Spacing.small))
                        manuals.forEach { manual ->
                            TextButton(onClick = { onOpenDocument(manual) }) {
                                Text(manual.originalFileName ?: "Manual")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.large).widthIn(max = Spacing.maxContentWidth))
    }
}

private fun sourceWording(source: ReturnPolicySource): String = when (source) {
    ReturnPolicySource.PRINTED_ON_RECEIPT -> "Printed on the receipt"
    ReturnPolicySource.SAVED_MERCHANT_RULE -> "Your saved rule for this shop"
    ReturnPolicySource.USER_ENTERED -> "You entered this"
    ReturnPolicySource.USER_CORRECTION -> "You corrected this"
    ReturnPolicySource.NONE -> "Not set"
}

private fun warrantyWording(provenance: WarrantyProvenance): String = when (provenance) {
    WarrantyProvenance.DOCUMENTED -> "From a document"
    WarrantyProvenance.USER_ENTERED -> "You entered this"
    WarrantyProvenance.SUGGESTED -> "Suggested by Keeply"
}

private fun readingWording(text: DocumentText): String = when {
    // Demo receipts were generated rather than read, and saying otherwise would
    // be a small lie in the one place Keeply is explaining where things come from.
    text.engine == "demo-data" -> "This is an example purchase. Its receipt was made up, not read."

    text.source == FieldSource.PDF_TEXT -> "Read directly from the PDF's own text."

    text.source == FieldSource.OCR -> text.meanConfidence?.let {
        "Read by text recognition, ${it.toInt()}% confident on average."
    } ?: "Read by text recognition."

    else -> "Entered by you."
}

private const val PREVIEW_HEIGHT = 520
private const val PREVIEW_WIDTH = 900
