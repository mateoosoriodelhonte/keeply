package app.keeply.services

import app.keeply.data.AttachmentRole
import app.keeply.data.KeeplyStore
import app.keeply.domain.CategoryId
import app.keeply.domain.DocumentId
import app.keeply.domain.LineItem
import app.keeply.domain.Merchant
import app.keeply.domain.MerchantId
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseDraft
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindowCalculator
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyCalculator
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * What a person decided on the review screen.
 *
 * Separate from the draft on purpose: the draft is what Keeply read, and this is
 * what the person says is true. Keeping them apart is what lets the detail view
 * show both, and what stops a correction quietly rewriting the reading it came
 * from.
 */
public data class ReviewedPurchase(
    val productName: String,
    val merchantName: String?,
    val merchantId: MerchantId? = null,
    val purchaseDate: LocalDate?,
    val price: Money?,
    val tax: Money? = null,
    val categoryId: CategoryId? = null,
    val receiptNumber: String? = null,
    val returnPolicy: ReturnPolicy = ReturnPolicy.Unknown,
    val returnPolicySource: ReturnPolicySource = ReturnPolicySource.NONE,
    val warrantyTerm: WarrantyTerm = WarrantyTerm.Unknown,
    val warrantyProvenance: WarrantyProvenance = WarrantyProvenance.USER_ENTERED,
    val warrantyNotes: String? = null,
    val paymentMethodLabel: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val serialNumber: String? = null,
    val lineItems: List<LineItem> = emptyList(),
    val receiptDocumentId: DocumentId? = null,
    val productPhotoId: DocumentId? = null,
    val isDemoData: Boolean = false,
)

/** Saving, editing and archiving purchases. */
public class PurchaseService(private val store: KeeplyStore, private val clock: Clock = Clock.systemDefaultZone()) {
    /**
     * Turns what a person confirmed into a saved purchase.
     *
     * A shop typed on the review screen is remembered, so the next receipt from it
     * is recognised and any return rule they save applies.
     */
    public fun save(reviewed: ReviewedPurchase, receiptText: String? = null, existingId: PurchaseId? = null): Purchase {
        val now = Instant.now(clock)
        val merchantId = reviewed.merchantId ?: reviewed.merchantName?.let(::rememberMerchant)

        val purchase = Purchase(
            id = existingId ?: PurchaseId.new(),
            productName = reviewed.productName.trim().ifEmpty { "Untitled purchase" },
            merchantId = merchantId,
            merchantName = reviewed.merchantName?.trim()?.takeIf { it.isNotEmpty() },
            purchaseDate = reviewed.purchaseDate,
            price = reviewed.price,
            tax = reviewed.tax,
            categoryId = reviewed.categoryId,
            receiptDocumentId = reviewed.receiptDocumentId,
            receiptNumber = reviewed.receiptNumber?.trim()?.takeIf { it.isNotEmpty() },
            returnWindow = ReturnWindowCalculator.resolve(
                reviewed.purchaseDate,
                reviewed.returnPolicy,
                reviewed.returnPolicySource,
            ),
            warranty = WarrantyCalculator.resolve(
                reviewed.purchaseDate,
                reviewed.warrantyTerm,
                reviewed.warrantyProvenance,
                reviewed.warrantyNotes,
            ),
            paymentMethodLabel = reviewed.paymentMethodLabel?.trim()?.takeIf { it.isNotEmpty() },
            notes = reviewed.notes?.trim()?.takeIf { it.isNotEmpty() },
            tags = reviewed.tags.map(String::trim).filter(String::isNotEmpty).distinct(),
            productPhotoId = reviewed.productPhotoId,
            serialNumber = reviewed.serialNumber?.trim()?.takeIf { it.isNotEmpty() },
            lineItems = reviewed.lineItems,
            isDemoData = reviewed.isDemoData,
            createdAt = existingId?.let { store.purchases.get(it)?.createdAt } ?: now,
            updatedAt = now,
        )
        store.purchases.save(purchase, receiptText)
        return purchase
    }

    /**
     * Builds the review screen's starting point from what Keeply read.
     *
     * Every value here is a proposal. Nothing is saved until a person says so.
     */
    public fun proposeFrom(draft: PurchaseDraft, receiptDocumentId: DocumentId?): ReviewedPurchase = ReviewedPurchase(
        productName = draft.productName?.value.orEmpty(),
        merchantName = draft.merchantName?.value,
        merchantId = draft.matchedMerchantId,
        purchaseDate = draft.purchaseDate?.value,
        price = draft.total?.value,
        tax = draft.tax?.value,
        receiptNumber = draft.receiptNumber?.value,
        returnPolicy = draft.suggestedReturnPolicy,
        returnPolicySource = draft.suggestedReturnSource,
        warrantyTerm = draft.suggestedWarranty,
        warrantyProvenance = WarrantyProvenance.SUGGESTED,
        lineItems = draft.lineItems,
        receiptDocumentId = receiptDocumentId,
    )

    public fun archive(id: PurchaseId, archived: Boolean = true) {
        store.purchases.setArchived(id, archived, Instant.now(clock))
    }

    public fun delete(id: PurchaseId) {
        store.purchases.delete(id)
    }

    public fun attach(purchaseId: PurchaseId, documentId: DocumentId, role: AttachmentRole) {
        store.purchases.attach(purchaseId, documentId, role)
    }

    /** Saves a shop the first time it is seen, so later receipts from it are recognised. */
    private fun rememberMerchant(name: String): MerchantId? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        store.merchants.findByName(trimmed)?.let { return it.id }
        val merchant = Merchant(id = MerchantId.new(), name = trimmed)
        store.merchants.save(merchant, Instant.now(clock))
        return merchant.id
    }
}
