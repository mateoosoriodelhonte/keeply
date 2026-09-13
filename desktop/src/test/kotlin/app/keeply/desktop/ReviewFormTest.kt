package app.keeply.desktop

import app.keeply.desktop.state.ReviewForm
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.Field
import app.keeply.domain.FieldSource
import app.keeply.domain.MerchantId
import app.keeply.domain.Money
import app.keeply.domain.PurchaseDraft
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.StoredDocument
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import app.keeply.services.ImportOutcome
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the review screen produces when somebody presses save.
 *
 * The rule under test throughout: **provenance follows what actually happened.**
 * A number somebody typed is recorded as theirs, and only a suggestion they left
 * alone keeps the source it arrived with. Getting this wrong means a deadline
 * somebody guessed at coming back later labelled as one Keeply read off a receipt.
 */
class ReviewFormTest {

    private fun outcome(
        merchantId: MerchantId? = MerchantId.new(),
        suggestedReturn: ReturnPolicy = ReturnPolicy.Unknown,
        suggestedSource: ReturnPolicySource = ReturnPolicySource.NONE,
        suggestedWarranty: WarrantyTerm = WarrantyTerm.Unknown,
    ) = ImportOutcome(
        document = StoredDocument(
            id = DocumentId.new(),
            kind = DocumentKind.RECEIPT,
            format = DocumentFormat.PNG,
            relativePath = "receipts/ab/x.png",
            byteSize = 1,
            sha256 = "a".repeat(64),
            importedAt = Instant.parse("2026-09-12T10:00:00Z"),
            originalFileName = "receipt.png",
        ),
        draft = PurchaseDraft(
            sourceDocumentId = null,
            productName = Field.uncertain("Headphones", FieldSource.OCR),
            merchantName = Field.uncertain("8EST 8UY", FieldSource.OCR, evidence = "8EST 8UY"),
            matchedMerchantId = merchantId,
            purchaseDate = Field.confident(LocalDate.of(2026, 9, 10), FieldSource.OCR),
            total = Field.confident(Money.of("249.99", CurrencyCode.USD), FieldSource.HEURISTIC),
            currency = Field.confident(CurrencyCode.USD, FieldSource.OCR),
            suggestedReturnPolicy = suggestedReturn,
            suggestedReturnSource = suggestedSource,
            suggestedWarranty = suggestedWarranty,
        ),
        notes = emptyList(),
        text = "",
        textSource = FieldSource.OCR,
        ocr = null,
        preprocessing = null,
        imageAssessment = null,
        duplicates = emptyList(),
        extensionMismatched = false,
    )

    @Test
    fun startsFromWhatKeeplyRead() {
        val form = ReviewForm(outcome())
        assertEquals("Headphones", form.productName)
        assertEquals("8EST 8UY", form.merchantName)
        assertEquals("2026-09-10", form.purchaseDate)
        assertEquals("249.99", form.total)
        assertTrue(form.canSave)
    }

    @Test
    fun aDeadlineTypedByThePersonIsRecordedAsTheirs() {
        val form = ReviewForm(outcome())
        form.edit(ReviewForm.Editable.RETURN_DAYS, "30")

        val saved = form.toReviewedPurchase()
        assertEquals(ReturnPolicy.Days(30), saved.returnPolicy)
        assertEquals(ReturnPolicySource.USER_ENTERED, saved.returnPolicySource)
    }

    @Test
    fun anUntouchedSuggestionKeepsWhereItCameFrom() {
        val form = ReviewForm(
            outcome(suggestedReturn = ReturnPolicy.Days(45), suggestedSource = ReturnPolicySource.PRINTED_ON_RECEIPT),
        )
        val saved = form.toReviewedPurchase()
        assertEquals(ReturnPolicy.Days(45), saved.returnPolicy)
        assertEquals(ReturnPolicySource.PRINTED_ON_RECEIPT, saved.returnPolicySource)
    }

    @Test
    fun changingASuggestionMakesItTheirs() {
        // Someone who overrides a rule printed on the receipt owns the new number.
        val form = ReviewForm(
            outcome(suggestedReturn = ReturnPolicy.Days(45), suggestedSource = ReturnPolicySource.PRINTED_ON_RECEIPT),
        )
        form.edit(ReviewForm.Editable.RETURN_DAYS, "14")

        val saved = form.toReviewedPurchase()
        assertEquals(ReturnPolicy.Days(14), saved.returnPolicy)
        assertEquals(ReturnPolicySource.USER_ENTERED, saved.returnPolicySource)
    }

    @Test
    fun aWarrantyKeeplySuggestedIsNotPresentedAsSomethingTheySaid() {
        val suggested = ReviewForm(outcome(suggestedWarranty = WarrantyTerm.Months(12))).toReviewedPurchase()
        assertEquals(WarrantyProvenance.SUGGESTED, suggested.warrantyProvenance)

        val form = ReviewForm(outcome(suggestedWarranty = WarrantyTerm.Months(12)))
        form.edit(ReviewForm.Editable.WARRANTY_MONTHS, "24")
        assertEquals(WarrantyProvenance.USER_ENTERED, form.toReviewedPurchase().warrantyProvenance)
        assertEquals(WarrantyTerm.Months(24), form.toReviewedPurchase().warrantyTerm)
    }

    @Test
    fun rewritingTheShopNameDropsTheShopKeeplyMatched() {
        // The saved shop came from text the person has now replaced, so it no longer
        // applies, and neither does any rule attached to it.
        val form = ReviewForm(outcome())
        assertTrue(form.toReviewedPurchase().merchantId != null)

        form.edit(ReviewForm.Editable.MERCHANT, "Northgate Electronics")
        val saved = form.toReviewedPurchase()
        assertNull(saved.merchantId)
        assertEquals("Northgate Electronics", saved.merchantName)
    }

    @Test
    fun readsWhateverWayThePersonTypedTheAmount() {
        val form = ReviewForm(outcome())
        form.edit(ReviewForm.Editable.TOTAL, "$1,019.72")
        assertEquals(Money.of("1019.72", CurrencyCode.USD), form.toReviewedPurchase().price)
    }

    @Test
    fun anUnreadableDateIsLeftEmptyRatherThanGuessed() {
        val form = ReviewForm(outcome())
        form.edit(ReviewForm.Editable.DATE, "sometime last spring")
        assertNull(form.toReviewedPurchase().purchaseDate)
    }

    @Test
    fun aPurchaseAlwaysEndsUpWithAName() {
        val form = ReviewForm(outcome())
        form.edit(ReviewForm.Editable.PRODUCT, "   ")
        assertEquals("8EST 8UY", form.toReviewedPurchase().productName)

        form.edit(ReviewForm.Editable.MERCHANT, "")
        assertFalse(form.canSave)
        assertEquals("Untitled purchase", form.toReviewedPurchase().productName)
    }

    @Test
    fun noDeadlineMeansNoSourceRatherThanAnEmptyClaim() {
        val saved = ReviewForm(outcome()).toReviewedPurchase()
        assertEquals(ReturnPolicy.Unknown, saved.returnPolicy)
        assertEquals(ReturnPolicySource.NONE, saved.returnPolicySource)
    }
}
