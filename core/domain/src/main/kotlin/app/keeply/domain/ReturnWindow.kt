package app.keeply.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How long a person has to take something back.
 *
 * Keeply never claims to know a shop's current returns policy. Every deadline it
 * shows can be traced to something the person told it or something printed on the
 * receipt, and [ReturnPolicySource] records which.
 */
public sealed interface ReturnPolicy {
    /** A number of days from the purchase date. */
    public data class Days(val days: Int) : ReturnPolicy {
        init {
            require(days >= 0) { "A return window cannot be negative, was $days" }
            require(days <= MAX_DAYS) { "A return window of $days days is not plausible" }
        }
    }

    /** A date printed on the receipt or typed by the person. */
    public data class Until(val date: LocalDate) : ReturnPolicy

    /** Keeply has nothing to go on, and says so. */
    public data object Unknown : ReturnPolicy

    public companion object {
        public const val MAX_DAYS: Int = 3_650
    }
}

/** Where the return deadline came from. Shown to the person so nothing looks authoritative that isn't. */
public enum class ReturnPolicySource {
    /** The person typed a number of days. */
    USER_ENTERED,

    /** A date or a window was printed on the receipt itself. */
    PRINTED_ON_RECEIPT,

    /** A default the person saved for this merchant. Their rule, not the shop's policy. */
    SAVED_MERCHANT_RULE,

    /** The person edited a deadline Keeply had worked out. */
    USER_CORRECTION,

    /** Nothing reliable was available. */
    NONE,
    ;

    /**
     * True when the person, rather than Keeply, is the authority for this deadline.
     * The interface uses this to avoid presenting a guess as a fact.
     */
    public val isUserAuthored: Boolean
        get() = this == USER_ENTERED || this == USER_CORRECTION || this == SAVED_MERCHANT_RULE
}

/** What Keeply shows next to a purchase. */
public enum class ReturnStatus {
    RETURNABLE,
    ENDS_SOON,
    ENDED,
    UNKNOWN,
}

/**
 * A return window resolved against a purchase date, ready to display.
 */
public data class ReturnWindow(val policy: ReturnPolicy, val source: ReturnPolicySource, val deadline: LocalDate?) {
    public fun statusOn(today: LocalDate, endsSoonWithinDays: Int = DEFAULT_ENDS_SOON_DAYS): ReturnStatus {
        val end = deadline ?: return ReturnStatus.UNKNOWN
        val remaining = ChronoUnit.DAYS.between(today, end)
        return when {
            remaining < 0 -> ReturnStatus.ENDED
            remaining <= endsSoonWithinDays -> ReturnStatus.ENDS_SOON
            else -> ReturnStatus.RETURNABLE
        }
    }

    /** Whole days left, or null when there is no known deadline. Negative once the window has closed. */
    public fun daysRemainingOn(today: LocalDate): Long? = deadline?.let { ChronoUnit.DAYS.between(today, it) }

    public companion object {
        public const val DEFAULT_ENDS_SOON_DAYS: Int = 7

        public val unknown: ReturnWindow =
            ReturnWindow(ReturnPolicy.Unknown, ReturnPolicySource.NONE, null)
    }
}

/**
 * Works out a deadline from a purchase date and a policy.
 *
 * Pure and date-injected, so the behaviour around month ends, leap days and
 * "today" is testable rather than dependent on when the tests happen to run.
 */
public object ReturnWindowCalculator {
    public fun resolve(purchaseDate: LocalDate?, policy: ReturnPolicy, source: ReturnPolicySource): ReturnWindow {
        val deadline = when (policy) {
            is ReturnPolicy.Until -> policy.date
            is ReturnPolicy.Days -> purchaseDate?.plusDays(policy.days.toLong())
            ReturnPolicy.Unknown -> null
        }
        val effectiveSource = if (deadline == null) ReturnPolicySource.NONE else source
        return ReturnWindow(policy, effectiveSource, deadline)
    }

    /**
     * Picks the best available policy for a purchase.
     *
     * A correction by the person beats a date printed on the receipt, which beats a
     * rule they saved for the merchant. Keeply prefers evidence it can point at.
     */
    public fun choose(
        userCorrection: ReturnPolicy? = null,
        printedOnReceipt: ReturnPolicy? = null,
        savedMerchantRule: ReturnPolicy? = null,
    ): Pair<ReturnPolicy, ReturnPolicySource> = when {
        userCorrection != null && userCorrection != ReturnPolicy.Unknown ->
            userCorrection to ReturnPolicySource.USER_CORRECTION

        printedOnReceipt != null && printedOnReceipt != ReturnPolicy.Unknown ->
            printedOnReceipt to ReturnPolicySource.PRINTED_ON_RECEIPT

        savedMerchantRule != null && savedMerchantRule != ReturnPolicy.Unknown ->
            savedMerchantRule to ReturnPolicySource.SAVED_MERCHANT_RULE

        else -> ReturnPolicy.Unknown to ReturnPolicySource.NONE
    }
}
