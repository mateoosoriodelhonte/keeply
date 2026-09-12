package app.keeply.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProvenanceTest {
    @Test
    fun anUncertainFieldAsksForReview() {
        val field = Field.uncertain("Best Buy", FieldSource.OCR, evidence = "8EST 8UY")
        assertTrue(field.needsReview)
        assertEquals("8EST 8UY", field.evidence)
    }

    @Test
    fun confirmingKeepsTheValueAndTheEvidence() {
        val field = Field.uncertain("Best Buy", FieldSource.OCR, evidence = "8EST 8UY").confirmed()
        assertEquals("Best Buy", field.value)
        assertEquals(FieldConfidence.CONFIRMED, field.confidence)
        assertEquals(FieldSource.OCR, field.source, "confirming does not rewrite where the value came from")
        assertFalse(field.needsReview)
    }

    @Test
    fun correctingReplacesTheReadingWithWhatThePersonTyped() {
        val read: Field<String> = Field.uncertain("8EST 8UY", FieldSource.OCR)
        val corrected = read.corrected("Best Buy")
        assertEquals("Best Buy", corrected.value)
        assertEquals(FieldSource.USER, corrected.source)
        assertTrue(corrected.source.isHuman)
    }

    @Test
    fun aDraftListsWhatStillNeedsALook() {
        val draft = PurchaseDraft(
            sourceDocumentId = DocumentId.new(),
            productName = Field.confident("Headphones", FieldSource.OCR),
            merchantName = Field.uncertain("8EST 8UY", FieldSource.OCR),
            total = Field.confident(Money.of("249.99", CurrencyCode.USD), FieldSource.HEURISTIC),
        )
        assertEquals(listOf("Store", "Purchase date"), draft.fieldsNeedingReview)
        assertTrue(draft.isUsable)
    }

    @Test
    fun aDraftWithNoTotalIsNotWorthShowingAsRead() {
        val draft = PurchaseDraft(sourceDocumentId = null)
        assertFalse(draft.isUsable)
        assertEquals(4, draft.fieldsNeedingReview.size)
    }

    @Test
    fun anAiSuggestionIsNeverTreatedAsAHumanDecision() {
        val suggestion = Field.uncertain("Wireless headphones", FieldSource.AI_SUGGESTION)
        assertFalse(suggestion.source.isHuman)
        assertTrue(suggestion.needsReview)
    }
}
