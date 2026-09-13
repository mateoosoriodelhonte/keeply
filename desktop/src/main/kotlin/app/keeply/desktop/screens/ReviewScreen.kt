package app.keeply.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.keeply.desktop.components.DocumentImage
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.components.rememberDocumentImage
import app.keeply.desktop.state.ReviewForm
import app.keeply.desktop.theme.Spacing
import app.keeply.documents.DataDirectory
import app.keeply.domain.CurrencyCode
import app.keeply.domain.Field
import app.keeply.domain.FieldConfidence
import app.keeply.domain.Money
import app.keeply.domain.MoneyParser
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import app.keeply.services.ImportOutcome
import app.keeply.services.ReviewedPurchase
import java.time.LocalDate

/**
 * What Keeply read, before anything is saved.
 *
 * The whole point of this screen is that nothing gets past it unseen. The receipt
 * is on the left and the fields are on the right, so checking a number means
 * glancing across rather than remembering. Anything Keeply was unsure of says so
 * next to the field, and editing a field marks it as the person's own.
 *
 * Cancelling here saves nothing. The imported file stays, so a person can try
 * again without hunting for it.
 */
@Composable
public fun ReviewScreen(
    outcome: ImportOutcome,
    directory: DataDirectory,
    onSave: (ReviewedPurchase) -> Unit,
    onCancel: () -> Unit,
    onSeeReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = outcome.draft
    val form = remember(outcome.document.id) { ReviewForm(outcome) }

    Row(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.medium),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val image = rememberDocumentImage(directory, outcome.document)) {
                DocumentImage.Loading -> CircularProgressIndicator()

                is DocumentImage.Failed -> Text(image.message, style = MaterialTheme.typography.bodyMedium)

                is DocumentImage.Ready -> Image(
                    bitmap = image.bitmap,
                    contentDescription = "The receipt you imported",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.page),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                text = "Check what Keeply read",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Nothing is saved until you say so. Change anything that looks wrong.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            outcome.advice?.let { advice ->
                Spacer(Modifier.height(Spacing.small))
                KeeplyCard { Text(advice, style = MaterialTheme.typography.bodyMedium) }
            }

            outcome.likelyDuplicate?.let { duplicate ->
                Spacer(Modifier.height(Spacing.small))
                KeeplyCard {
                    Text(
                        text = duplicate.explain(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Keeply has not removed anything. Add it anyway if you meant to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.small))
            ReviewField("What is it?", form, ReviewForm.Editable.PRODUCT, form.productName, draft.productName)
            ReviewField("Where from?", form, ReviewForm.Editable.MERCHANT, form.merchantName, draft.merchantName)
            ReviewField("When?", form, ReviewForm.Editable.DATE, form.purchaseDate, draft.purchaseDate, "YYYY-MM-DD")
            ReviewField("How much?", form, ReviewForm.Editable.TOTAL, form.total, draft.total, form.currency.code)
            ReviewField(
                "Receipt number",
                form,
                ReviewForm.Editable.RECEIPT_NUMBER,
                form.receiptNumber,
                draft.receiptNumber,
            )

            Spacer(Modifier.height(Spacing.medium))
            Text(
                text = "Returns and warranty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = returnSourceExplanation(draft.suggestedReturnSource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReviewField("Days to return", form, ReviewForm.Editable.RETURN_DAYS, form.returnDays, null, "for example 30")
            ReviewField(
                "Warranty, in months",
                form,
                ReviewForm.Editable.WARRANTY_MONTHS,
                form.warrantyMonths,
                null,
                "for example 24",
            )

            Spacer(Modifier.height(Spacing.small))
            OutlinedTextField(
                value = form.notes,
                onValueChange = { form.edit(ReviewForm.Editable.NOTES, it) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(Spacing.large))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Button(
                    onClick = { onSave(form.toReviewedPurchase()) },
                    enabled = form.canSave,
                ) { Text("Save purchase") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onSeeReading) { Text("See what Keeply read") }
            }
            Spacer(Modifier.height(Spacing.large))
        }
    }
}

/**
 * One field, with a note underneath when Keeply was not sure.
 *
 * The note disappears once a person edits the field, because at that point the
 * value came from them and Keeply's opinion of its own reading is no longer
 * relevant.
 */
@Composable
private fun ReviewField(
    label: String,
    form: ReviewForm,
    editable: ReviewForm.Editable,
    value: String,
    field: Field<*>?,
    hint: String? = null,
) {
    val wasEdited = form.wasEdited(editable)
    val uncertain = field?.needsReview == true && !wasEdited
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.tight)) {
        OutlinedTextField(
            value = value,
            onValueChange = { form.edit(editable, it) },
            label = { Text(label) },
            placeholder = hint?.let { { Text(it) } },
            singleLine = true,
            isError = false,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            wasEdited -> Note("Your correction", MaterialTheme.colorScheme.primary)
            uncertain && field.evidence != null -> Note("Keeply wasn't sure. It read: ${field.evidence}")
            uncertain -> Note("Keeply wasn't sure about this one.")
            field?.confidence == FieldConfidence.CONFIDENT -> Note("Read from the receipt")
            else -> Unit
        }
    }
}

@Composable
private fun Note(text: String, colour: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colour,
        modifier = Modifier.padding(start = Spacing.tight, top = 2.dp),
    )
}

private fun returnSourceExplanation(source: ReturnPolicySource): String = when (source) {
    ReturnPolicySource.PRINTED_ON_RECEIPT -> "Keeply found a returns line printed on this receipt."
    ReturnPolicySource.SAVED_MERCHANT_RULE -> "Using the rule you saved for this shop. It is your rule, not the shop's policy."
    else -> "Keeply does not know this shop's returns policy. Enter the number of days if you know it."
}
