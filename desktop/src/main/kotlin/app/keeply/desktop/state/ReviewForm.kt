package app.keeply.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keeply.domain.CurrencyCode
import app.keeply.domain.MoneyParser
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import app.keeply.services.ImportOutcome
import app.keeply.services.ReviewedPurchase
import java.time.LocalDate

/**
 * The review screen's fields, and what saving them produces.
 *
 * Kept out of the composable so the rule that matters can be tested without
 * driving an interface: **provenance follows what actually happened**. A number
 * somebody typed is recorded as theirs, and only a suggestion they left alone
 * keeps the source it arrived with. Getting this wrong would mean a deadline
 * somebody guessed at coming back later labelled as one Keeply read off a
 * receipt, which is the kind of quiet wrongness that costs a refund.
 */
public class ReviewForm(private val outcome: ImportOutcome) {
    private val draft = outcome.draft

    public val currency: CurrencyCode = draft.currency?.value ?: CurrencyCode.USD

    public var productName: String by mutableStateOf(draft.productName?.value.orEmpty())
        private set
    public var merchantName: String by mutableStateOf(draft.merchantName?.value.orEmpty())
        private set
    public var purchaseDate: String by mutableStateOf(draft.purchaseDate?.value?.toString().orEmpty())
        private set
    public var total: String by mutableStateOf(draft.total?.value?.toPlainString().orEmpty())
        private set
    public var receiptNumber: String by mutableStateOf(draft.receiptNumber?.value.orEmpty())
        private set
    public var returnDays: String by mutableStateOf(
        (draft.suggestedReturnPolicy as? ReturnPolicy.Days)?.days?.toString().orEmpty(),
    )
        private set
    public var warrantyMonths: String by mutableStateOf(
        (draft.suggestedWarranty as? WarrantyTerm.Months)?.months?.toString().orEmpty(),
    )
        private set
    public var notes: String by mutableStateOf("")
        private set

    private val edited = mutableSetOf<Editable>()

    public enum class Editable { PRODUCT, MERCHANT, DATE, TOTAL, RECEIPT_NUMBER, RETURN_DAYS, WARRANTY_MONTHS, NOTES }

    public fun wasEdited(field: Editable): Boolean = field in edited

    public fun edit(field: Editable, value: String) {
        edited += field
        when (field) {
            Editable.PRODUCT -> productName = value
            Editable.MERCHANT -> merchantName = value
            Editable.DATE -> purchaseDate = value
            Editable.TOTAL -> total = value
            Editable.RECEIPT_NUMBER -> receiptNumber = value
            Editable.RETURN_DAYS -> returnDays = value
            Editable.WARRANTY_MONTHS -> warrantyMonths = value
            Editable.NOTES -> notes = value
        }
    }

    /** Enough has been entered for the purchase to be worth saving. */
    public val canSave: Boolean get() = productName.isNotBlank() || merchantName.isNotBlank()

    public fun toReviewedPurchase(): ReviewedPurchase {
        val days = returnDays.trim().toIntOrNull()
        val months = warrantyMonths.trim().toIntOrNull()

        return ReviewedPurchase(
            productName = productName.trim().ifBlank { merchantName.trim().ifBlank { "Untitled purchase" } },
            merchantName = merchantName.trim().takeIf { it.isNotEmpty() },
            // A shop whose name the person rewrote is no longer the one Keeply matched.
            merchantId = draft.matchedMerchantId.takeUnless { wasEdited(Editable.MERCHANT) },
            purchaseDate = runCatching { LocalDate.parse(purchaseDate.trim()) }.getOrNull(),
            price = MoneyParser(currency).parse(total),
            tax = draft.tax?.value,
            receiptNumber = receiptNumber.trim().takeIf { it.isNotEmpty() },
            returnPolicy = days?.let { runCatching { ReturnPolicy.Days(it) }.getOrNull() } ?: ReturnPolicy.Unknown,
            returnPolicySource = when {
                days == null -> ReturnPolicySource.NONE
                wasEdited(Editable.RETURN_DAYS) -> ReturnPolicySource.USER_ENTERED
                else -> draft.suggestedReturnSource
            },
            warrantyTerm = months?.let { runCatching { WarrantyTerm.Months(it) }.getOrNull() } ?: WarrantyTerm.Unknown,
            warrantyProvenance = if (wasEdited(Editable.WARRANTY_MONTHS)) {
                WarrantyProvenance.USER_ENTERED
            } else {
                WarrantyProvenance.SUGGESTED
            },
            notes = notes.trim().takeIf { it.isNotEmpty() },
            lineItems = draft.lineItems,
            receiptDocumentId = outcome.document.id,
        )
    }
}
