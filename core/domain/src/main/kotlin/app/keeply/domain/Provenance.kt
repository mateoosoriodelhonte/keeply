package app.keeply.domain

/**
 * How sure Keeply is about a value it filled in for you.
 *
 * The whole point of the review step is that this is visible: a person should never
 * have to guess whether Keeply read a number or guessed it.
 */
public enum class FieldConfidence {
    /** Read cleanly, and consistent with the rest of the receipt. */
    CONFIDENT,

    /** Read, but something did not add up. Worth a glance before saving. */
    UNCERTAIN,

    /** A person looked at this and said it is right. */
    CONFIRMED,
    ;

    public val needsReview: Boolean get() = this == UNCERTAIN
}

/** Where a value came from. Shown in the detail view and in the OCR debug view. */
public enum class FieldSource {
    /** Text layer embedded in a PDF. No OCR was involved, so this is trustworthy. */
    PDF_TEXT,

    /** Optical character recognition on an image. */
    OCR,

    /** Derived by a parsing rule, for example a total that matches subtotal plus tax. */
    HEURISTIC,

    /** A rule the person saved for this merchant, such as their own return window. */
    SAVED_RULE,

    /** Typed or corrected by the person using Keeply. */
    USER,

    /** Proposed by optional local AI. Never applied without the person accepting it. */
    AI_SUGGESTION,
    ;

    /** True when the value came from a person rather than from a machine reading. */
    public val isHuman: Boolean get() = this == USER
}

/**
 * A value together with how it was obtained.
 *
 * A field that could not be read is simply absent. Keeply does not store a
 * placeholder and does not fabricate a value to fill a gap.
 */
public data class Field<out T : Any>(
    val value: T,
    val confidence: FieldConfidence,
    val source: FieldSource,
    /** The snippet of text this was read from, kept so a person can check the reading. */
    val evidence: String? = null,
) {
    public val needsReview: Boolean get() = confidence.needsReview

    public fun <R : Any> map(transform: (T) -> R): Field<R> = Field(transform(value), confidence, source, evidence)

    /** Marks this value as checked by a person, without changing the value. */
    public fun confirmed(): Field<T> = copy(confidence = FieldConfidence.CONFIRMED)

    public companion object {
        public fun <T : Any> userEntered(value: T): Field<T> = Field(value, FieldConfidence.CONFIRMED, FieldSource.USER)

        public fun <T : Any> confident(value: T, source: FieldSource, evidence: String? = null): Field<T> =
            Field(value, FieldConfidence.CONFIDENT, source, evidence)

        public fun <T : Any> uncertain(value: T, source: FieldSource, evidence: String? = null): Field<T> =
            Field(value, FieldConfidence.UNCERTAIN, source, evidence)
    }
}

/** Replaces a machine reading with what a person typed. */
public fun <T : Any> Field<T>?.corrected(value: T): Field<T> = Field.userEntered(value)
