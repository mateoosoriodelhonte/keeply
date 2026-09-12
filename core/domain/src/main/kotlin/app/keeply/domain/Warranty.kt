package app.keeply.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How long a warranty lasts. */
public sealed interface WarrantyTerm {
    public data class Months(val months: Int) : WarrantyTerm {
        init {
            require(months in 1..MAX_MONTHS) { "A warranty of $months months is not plausible" }
        }
    }

    public data class Days(val days: Int) : WarrantyTerm {
        init {
            require(days in 1..MAX_DAYS) { "A warranty of $days days is not plausible" }
        }
    }

    public data class Until(val date: LocalDate) : WarrantyTerm

    public data object Lifetime : WarrantyTerm

    public data object Unknown : WarrantyTerm

    public companion object {
        public const val MAX_MONTHS: Int = 1_200
        public const val MAX_DAYS: Int = 36_500
    }
}

/**
 * Where the warranty information came from.
 *
 * Keeply shows this because "the receipt says so" and "I think it was two years"
 * are different kinds of claim, and a person deserves to know which they are
 * looking at before they throw a box away.
 */
public enum class WarrantyProvenance {
    /** Read from a receipt or a warranty document that Keeply still has. */
    DOCUMENTED,

    /** Typed in by the person. */
    USER_ENTERED,

    /** Proposed by Keeply, for example from a category default. Always marked as a suggestion. */
    SUGGESTED,
}

public enum class WarrantyStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    LIFETIME,
    UNKNOWN,
}

public data class Warranty(
    val term: WarrantyTerm,
    val provenance: WarrantyProvenance,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    /** Free text from a warranty document, kept verbatim. */
    val notes: String? = null,
) {
    public fun statusOn(today: LocalDate, expiringWithinDays: Int = DEFAULT_EXPIRING_SOON_DAYS): WarrantyStatus {
        if (term is WarrantyTerm.Lifetime) return WarrantyStatus.LIFETIME
        val end = endDate ?: return WarrantyStatus.UNKNOWN
        val remaining = ChronoUnit.DAYS.between(today, end)
        return when {
            remaining < 0 -> WarrantyStatus.EXPIRED
            remaining <= expiringWithinDays -> WarrantyStatus.EXPIRING_SOON
            else -> WarrantyStatus.ACTIVE
        }
    }

    public fun daysRemainingOn(today: LocalDate): Long? = endDate?.let { ChronoUnit.DAYS.between(today, it) }

    public companion object {
        public const val DEFAULT_EXPIRING_SOON_DAYS: Int = 30

        public val unknown: Warranty =
            Warranty(WarrantyTerm.Unknown, WarrantyProvenance.SUGGESTED, null, null)
    }
}

public object WarrantyCalculator {
    /**
     * Resolves a warranty against the date cover starts, which is normally the
     * purchase date but can be a delivery or installation date the person entered.
     */
    public fun resolve(coverStarts: LocalDate?, term: WarrantyTerm, provenance: WarrantyProvenance, notes: String? = null): Warranty {
        val end = when (term) {
            is WarrantyTerm.Until -> term.date
            is WarrantyTerm.Months -> coverStarts?.plusMonths(term.months.toLong())
            is WarrantyTerm.Days -> coverStarts?.plusDays(term.days.toLong())
            WarrantyTerm.Lifetime, WarrantyTerm.Unknown -> null
        }
        return Warranty(
            term = term,
            provenance = provenance,
            startDate = coverStarts,
            endDate = end,
            notes = notes,
        )
    }
}
