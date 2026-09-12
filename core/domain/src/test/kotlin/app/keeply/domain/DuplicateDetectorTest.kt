package app.keeply.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuplicateDetectorTest {
    private val existingId = PurchaseId.new()
    private val date = LocalDate.of(2026, 9, 12)

    private fun fingerprint(
        id: PurchaseId? = existingId,
        sha: String? = "a".repeat(64),
        merchant: String? = "best buy",
        day: LocalDate? = date,
        total: Money? = Money.of("249.99", CurrencyCode.USD),
        receiptNumber: String? = "T-0099",
    ) = DuplicateFingerprint(id, sha, merchant, day, total, receiptNumber)

    @Test
    fun theSameFileIsCertain() {
        val candidate = DuplicateDetector.compare(fingerprint(id = null), fingerprint())
        assertEquals(DuplicateVerdict.CERTAIN, candidate?.verdict)
        assertTrue(DuplicateSignal.IDENTICAL_FILE in candidate!!.signals)
        assertEquals("This is the same file you already imported.", candidate.explain())
    }

    @Test
    fun theSameReceiptNumberAtTheSameShopIsCertain() {
        val incoming = fingerprint(id = null, sha = "b".repeat(64))
        val candidate = DuplicateDetector.compare(incoming, fingerprint())
        assertEquals(DuplicateVerdict.CERTAIN, candidate?.verdict)
    }

    @Test
    fun sameShopDateAndTotalIsLikelyEnoughToAsk() {
        val incoming = fingerprint(id = null, sha = "b".repeat(64), receiptNumber = null)
        val candidate = DuplicateDetector.compare(incoming, fingerprint())
        assertEquals(DuplicateVerdict.LIKELY, candidate?.verdict)
        assertEquals("A purchase with the same store, date and total is already saved.", candidate?.explain())
    }

    @Test
    fun toleratesTheOddMisreadDigitAndDate() {
        // OCR gets the last cent or the day wrong often enough that exact matching
        // would miss real duplicates.
        val incoming = fingerprint(
            id = null,
            sha = "b".repeat(64),
            receiptNumber = null,
            day = date.plusDays(1),
            total = Money.of("250.00", CurrencyCode.USD),
        )
        val candidate = DuplicateDetector.compare(incoming, fingerprint())
        assertTrue(DuplicateSignal.SAME_DATE in candidate!!.signals)
        assertTrue(DuplicateSignal.SAME_TOTAL in candidate.signals)
        assertEquals(DuplicateVerdict.LIKELY, candidate.verdict)
    }

    @Test
    fun aDifferentPurchaseIsNotFlagged() {
        val incoming = DuplicateFingerprint(
            purchaseId = null,
            fileSha256 = "b".repeat(64),
            merchantKey = "target",
            purchaseDate = LocalDate.of(2025, 1, 1),
            total = Money.of("9.99", CurrencyCode.USD),
            receiptNumber = "XYZ",
        )
        assertNull(DuplicateDetector.compare(incoming, fingerprint()))
    }

    @Test
    fun differentCurrenciesAreNeverTheSameTotal() {
        val incoming = fingerprint(
            id = null,
            sha = "b".repeat(64),
            receiptNumber = null,
            total = Money(24999, CurrencyCode.EUR),
        )
        val candidate = DuplicateDetector.compare(incoming, fingerprint())
        assertEquals(false, DuplicateSignal.SAME_TOTAL in candidate!!.signals)
    }

    @Test
    fun aReceiptNumberAloneIsNotEnoughAcrossDifferentShops() {
        val incoming = DuplicateFingerprint(
            purchaseId = null,
            fileSha256 = "b".repeat(64),
            merchantKey = "target",
            purchaseDate = null,
            total = null,
            receiptNumber = "T-0099",
        )
        // Till numbers collide between shops; without the same merchant this means nothing.
        assertNull(DuplicateDetector.compare(incoming, fingerprint()))
    }

    @Test
    fun ranksTheStrongestCandidateFirst() {
        val exact = fingerprint(id = PurchaseId.new())
        val similar = fingerprint(id = PurchaseId.new(), sha = "c".repeat(64), receiptNumber = null)
        val incoming = fingerprint(id = null)

        val results = DuplicateDetector.findAll(incoming, listOf(similar, exact))
        assertEquals(2, results.size)
        assertEquals(exact.purchaseId, results.first().purchaseId)
        assertEquals(DuplicateVerdict.CERTAIN, results.first().verdict)
    }

    @Test
    fun neverComparesAgainstAnUnsavedRecord() {
        assertNull(DuplicateDetector.compare(fingerprint(id = null), fingerprint(id = null)))
    }
}
