package app.keeply.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * An ISO 4217 currency code.
 *
 * Keeply validates the code on construction because a receipt that produced
 * nonsense here would otherwise quietly corrupt every total the person sees.
 */
@JvmInline
public value class CurrencyCode(public val code: String) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) {
            "Currency code must be three uppercase letters, was '$code'"
        }
    }

    public val javaCurrency: Currency
        get() = Currency.getInstance(code)

    /** Number of decimal places this currency uses. Most use 2; JPY uses 0. */
    public val minorUnitDigits: Int
        get() = javaCurrency.defaultFractionDigits.coerceAtLeast(0)

    override fun toString(): String = code

    public companion object {
        public val USD: CurrencyCode = CurrencyCode("USD")
        public val EUR: CurrencyCode = CurrencyCode("EUR")
        public val GBP: CurrencyCode = CurrencyCode("GBP")

        public fun isSupported(code: String): Boolean = runCatching { Currency.getInstance(code.uppercase(Locale.ROOT)) }.isSuccess

        public fun orNull(code: String): CurrencyCode? {
            val upper = code.uppercase(Locale.ROOT)
            if (upper.length != 3 || !upper.all { it in 'A'..'Z' } || !isSupported(upper)) return null
            return CurrencyCode(upper)
        }
    }
}

/**
 * An amount of money, held as a whole number of minor units (cents, pence, yen).
 *
 * Receipt totals are added, compared and stored, and binary floating point gets those
 * wrong in ways people notice. There is no [Double] anywhere in this type.
 */
public data class Money(val amountMinor: Long, val currency: CurrencyCode) : Comparable<Money> {
    public val isZero: Boolean get() = amountMinor == 0L
    public val isNegative: Boolean get() = amountMinor < 0L

    public operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.addExact(amountMinor, other.amountMinor))
    }

    public operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.subtractExact(amountMinor, other.amountMinor))
    }

    public operator fun times(factor: Int): Money = copy(amountMinor = Math.multiplyExact(amountMinor, factor.toLong()))

    public fun absoluteDifference(other: Money): Long {
        requireSameCurrency(other)
        return Math.abs(Math.subtractExact(amountMinor, other.amountMinor))
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    /** The amount as a decimal, for display and export. Never used for arithmetic. */
    public fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amountMinor, currency.minorUnitDigits)

    /** A plain machine-readable form such as `249.99`, used by CSV and JSON export. */
    public fun toPlainString(): String = toBigDecimal().toPlainString()

    /** A form for people to read, such as `$249.99`, in the given locale. */
    public fun format(locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = currency.javaCurrency
        formatter.minimumFractionDigits = currency.minorUnitDigits
        formatter.maximumFractionDigits = currency.minorUnitDigits
        return formatter.format(toBigDecimal())
    }

    override fun toString(): String = "${toPlainString()} $currency"

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine ${currency.code} with ${other.currency.code}"
        }
    }

    public companion object {
        public fun zero(currency: CurrencyCode): Money = Money(0, currency)

        /** Builds an amount from a decimal, rounding half-up to the currency's precision. */
        public fun of(amount: BigDecimal, currency: CurrencyCode): Money = Money(
            amount.setScale(currency.minorUnitDigits, RoundingMode.HALF_UP)
                .movePointRight(currency.minorUnitDigits)
                .longValueExact(),
            currency,
        )

        public fun of(amount: String, currency: CurrencyCode): Money = of(BigDecimal(amount), currency)
    }
}
