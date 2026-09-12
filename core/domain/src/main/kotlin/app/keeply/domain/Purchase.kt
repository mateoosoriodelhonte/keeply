package app.keeply.domain

import java.time.Instant
import java.time.LocalDate

/** One line on a receipt. */
public data class LineItem(
    val description: String,
    val quantity: Int? = null,
    val unitPrice: Money? = null,
    val totalPrice: Money? = null,
    val confidence: FieldConfidence = FieldConfidence.UNCERTAIN,
) {
    init {
        require(description.isNotBlank()) { "A line item needs a description" }
        require(quantity == null || quantity > 0) { "Quantity must be positive, was $quantity" }
    }
}

/**
 * A thing the person bought.
 *
 * This is what Keeply exists to remember. Fields the app could not establish are
 * null rather than blank or zero, so nothing invented can be mistaken for something
 * read off a receipt.
 */
public data class Purchase(
    val id: PurchaseId,
    val productName: String,
    val merchantId: MerchantId?,
    val merchantName: String?,
    val purchaseDate: LocalDate?,
    val price: Money?,
    val tax: Money? = null,
    val categoryId: CategoryId?,
    val receiptDocumentId: DocumentId?,
    val receiptNumber: String? = null,
    val returnWindow: ReturnWindow = ReturnWindow.unknown,
    val warranty: Warranty = Warranty.unknown,
    /** A label such as "Visa ending 1234" that the person typed. Keeply never stores card details. */
    val paymentMethodLabel: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val productPhotoId: DocumentId? = null,
    val manualDocumentIds: List<DocumentId> = emptyList(),
    val warrantyDocumentIds: List<DocumentId> = emptyList(),
    val serialNumber: String? = null,
    val lineItems: List<LineItem> = emptyList(),
    val isArchived: Boolean = false,
    val isDemoData: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(productName.isNotBlank()) { "A purchase needs a product name" }
    }

    public fun returnStatusOn(today: LocalDate): ReturnStatus = returnWindow.statusOn(today)

    public fun warrantyStatusOn(today: LocalDate): WarrantyStatus = warranty.statusOn(today)

    /** True when this purchase belongs on the home screen's "needs attention" list. */
    public fun needsAttentionOn(today: LocalDate): Boolean = !isArchived &&
        (returnStatusOn(today) == ReturnStatus.ENDS_SOON || warrantyStatusOn(today) == WarrantyStatus.EXPIRING_SOON)
}

/**
 * A purchase that has been read off a receipt but not yet saved.
 *
 * A draft holds provenance for every field, which is what the review screen shows.
 * Turning a draft into a [Purchase] is a deliberate act by a person.
 */
public data class PurchaseDraft(
    val sourceDocumentId: DocumentId?,
    val productName: Field<String>? = null,
    val merchantName: Field<String>? = null,
    val matchedMerchantId: MerchantId? = null,
    val purchaseDate: Field<LocalDate>? = null,
    val total: Field<Money>? = null,
    val subtotal: Field<Money>? = null,
    val tax: Field<Money>? = null,
    val currency: Field<CurrencyCode>? = null,
    val receiptNumber: Field<String>? = null,
    val lineItems: List<LineItem> = emptyList(),
    val suggestedReturnPolicy: ReturnPolicy = ReturnPolicy.Unknown,
    val suggestedReturnSource: ReturnPolicySource = ReturnPolicySource.NONE,
    val suggestedWarranty: WarrantyTerm = WarrantyTerm.Unknown,
) {
    /** Fields a person should look at before saving. */
    public val fieldsNeedingReview: List<String>
        get() = buildList {
            if (productName?.needsReview != false) add("Product")
            if (merchantName?.needsReview != false) add("Store")
            if (purchaseDate?.needsReview != false) add("Purchase date")
            if (total?.needsReview != false) add("Total")
        }

    /** True when Keeply read enough that the review screen is a check rather than data entry. */
    public val isUsable: Boolean
        get() = total != null && (merchantName != null || productName != null)
}
