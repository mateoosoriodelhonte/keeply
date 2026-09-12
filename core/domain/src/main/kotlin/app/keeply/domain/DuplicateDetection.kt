package app.keeply.domain

import java.time.LocalDate
import kotlin.math.abs

/** A reason to think two receipts are the same receipt. */
public enum class DuplicateSignal(public val weight: Int) {
    /** Byte-for-byte the same file. Nothing else needs to agree. */
    IDENTICAL_FILE(100),

    /** The same receipt or order number at the same merchant. */
    SAME_RECEIPT_NUMBER(60),

    SAME_MERCHANT(15),
    SAME_DATE(20),
    SAME_TOTAL(30),
    ;

    public companion object {
        public const val CERTAIN_SCORE: Int = 100
        public const val LIKELY_SCORE: Int = 60
        public const val POSSIBLE_SCORE: Int = 35
    }
}

public enum class DuplicateVerdict {
    /** The same bytes, or the same receipt number at the same shop. */
    CERTAIN,

    /** Enough agrees that a person should be asked. */
    LIKELY,

    /** Worth mentioning, not worth interrupting for. */
    POSSIBLE,

    /** Not a duplicate. */
    DIFFERENT,
}

public data class DuplicateCandidate(
    val purchaseId: PurchaseId,
    val signals: Set<DuplicateSignal>,
    val score: Int,
    val verdict: DuplicateVerdict,
) {
    /** Wording for the prompt. Keeply asks; it never removes anything by itself. */
    public fun explain(): String = when {
        DuplicateSignal.IDENTICAL_FILE in signals -> "This is the same file you already imported."
        DuplicateSignal.SAME_RECEIPT_NUMBER in signals -> "A purchase with this receipt number is already saved."
        else -> "A purchase with the same store, date and total is already saved."
    }
}

/**
 * Facts about a receipt that are worth comparing against what is already saved.
 */
public data class DuplicateFingerprint(
    val purchaseId: PurchaseId?,
    val fileSha256: String?,
    val merchantKey: String?,
    val purchaseDate: LocalDate?,
    val total: Money?,
    val receiptNumber: String?,
)

/**
 * Decides whether a receipt being imported is one Keeply already has.
 *
 * Keeply never deletes or merges anything on its own. The strongest thing this
 * produces is a question: this may already be in Keeply, add anyway?
 */
public object DuplicateDetector {
    /** Totals within a cent or two still count as the same, since OCR misreads the odd digit. */
    private const val TOTAL_TOLERANCE_MINOR = 2L

    /** Receipts imported a day apart can carry neighbouring dates after a misread. */
    private const val DATE_TOLERANCE_DAYS = 1L

    public fun compare(incoming: DuplicateFingerprint, existing: DuplicateFingerprint): DuplicateCandidate? {
        val existingId = existing.purchaseId ?: return null
        val signals = mutableSetOf<DuplicateSignal>()

        if (!incoming.fileSha256.isNullOrBlank() && incoming.fileSha256 == existing.fileSha256) {
            signals += DuplicateSignal.IDENTICAL_FILE
        }

        val sameMerchant = !incoming.merchantKey.isNullOrBlank() && incoming.merchantKey == existing.merchantKey
        if (sameMerchant) signals += DuplicateSignal.SAME_MERCHANT

        if (!incoming.receiptNumber.isNullOrBlank() &&
            incoming.receiptNumber.equals(existing.receiptNumber, ignoreCase = true) &&
            sameMerchant
        ) {
            signals += DuplicateSignal.SAME_RECEIPT_NUMBER
        }

        if (incoming.purchaseDate != null && existing.purchaseDate != null) {
            val apart = abs(incoming.purchaseDate.toEpochDay() - existing.purchaseDate.toEpochDay())
            if (apart <= DATE_TOLERANCE_DAYS) signals += DuplicateSignal.SAME_DATE
        }

        val incomingTotal = incoming.total
        val existingTotal = existing.total
        if (incomingTotal != null && existingTotal != null &&
            incomingTotal.currency == existingTotal.currency &&
            incomingTotal.absoluteDifference(existingTotal) <= TOTAL_TOLERANCE_MINOR
        ) {
            signals += DuplicateSignal.SAME_TOTAL
        }

        if (signals.isEmpty()) return null

        val score = signals.sumOf { it.weight }
        val verdict = when {
            score >= DuplicateSignal.CERTAIN_SCORE -> DuplicateVerdict.CERTAIN
            score >= DuplicateSignal.LIKELY_SCORE -> DuplicateVerdict.LIKELY
            score >= DuplicateSignal.POSSIBLE_SCORE -> DuplicateVerdict.POSSIBLE
            else -> DuplicateVerdict.DIFFERENT
        }
        if (verdict == DuplicateVerdict.DIFFERENT) return null
        return DuplicateCandidate(existingId, signals, score, verdict)
    }

    /** The strongest candidates first, so the interface can lead with the clearest one. */
    public fun findAll(incoming: DuplicateFingerprint, existing: List<DuplicateFingerprint>): List<DuplicateCandidate> =
        existing.mapNotNull { compare(incoming, it) }.sortedByDescending { it.score }
}
