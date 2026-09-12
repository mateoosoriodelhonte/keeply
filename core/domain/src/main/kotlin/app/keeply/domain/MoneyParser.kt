package app.keeply.domain

import java.math.BigDecimal
import java.util.Locale

/**
 * Turns the many ways an amount can be written into a [Money].
 *
 * This has to cope with what a till prints and what a person types: `$249.99`,
 * `249,99 EUR`, `1.234,56`, `(12.34)` for a refund, `12.34-`, and a bare `12`.
 *
 * When the text is ambiguous the parser gives up and returns null. A receipt that
 * cannot be read is a field Keeply leaves empty, never a number it invented.
 */
public class MoneyParser(private val defaultCurrency: CurrencyCode = CurrencyCode.USD) {
    public fun parse(raw: String): Money? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        var working = trimmed
        var negative = false

        // (12.34) is how accounting notation and many tills write a refund.
        if (working.startsWith("(") && working.endsWith(")")) {
            negative = true
            working = working.substring(1, working.length - 1).trim()
        }
        // A trailing minus is common on point-of-sale printouts.
        if (working.endsWith("-")) {
            negative = true
            working = working.dropLast(1).trim()
        }
        if (working.startsWith("-")) {
            negative = !negative
            working = working.drop(1).trim()
        }
        if (working.startsWith("+")) {
            working = working.drop(1).trim()
        }

        val currency = detectCurrency(working) ?: defaultCurrency
        val digits = working.filter { it.isDigit() || it == '.' || it == ',' }
        if (digits.none { it.isDigit() }) return null

        val decimal = toDecimal(digits) ?: return null
        val money = Money.of(decimal, currency)
        return if (negative) Money(-money.amountMinor, currency) else money
    }

    /** Parses text that is already known to be in [currency], such as a total column. */
    public fun parseIn(raw: String, currency: CurrencyCode): Money? =
        MoneyParser(currency).parse(raw)?.let { Money(it.amountMinor, currency) }

    private fun detectCurrency(text: String): CurrencyCode? {
        val upper = text.uppercase(Locale.ROOT)

        // An explicit three-letter code wins over a symbol: "CA$" and "CAD" mean the
        // same thing but only one of them is unambiguous.
        CODE_PATTERN.findAll(upper).forEach { match ->
            CurrencyCode.orNull(match.value)?.let { return it }
        }

        for ((symbol, code) in SYMBOLS) {
            if (text.contains(symbol)) {
                // "$" is used by many currencies. If the caller's default is one of
                // them, respect that rather than assuming US dollars.
                if (symbol == "$" && defaultCurrency.javaCurrency.getSymbol(Locale.US).contains("$")) {
                    return defaultCurrency
                }
                return code
            }
        }
        return null
    }

    /**
     * Works out which of `.` and `,` introduces the fraction, which is the whole
     * problem with reading money out of text, and rejects anything whose digit
     * grouping does not make sense as a number.
     */
    private fun toDecimal(digits: String): BigDecimal? {
        val lastDot = digits.lastIndexOf('.')
        val lastComma = digits.lastIndexOf(',')

        // With both present, the rightmost one is the fraction separator: 1.234,56
        // and 1,234.56 are the same amount written by different conventions.
        val decimalSeparator: Char?
        val groupingSeparator: Char?
        when {
            lastDot >= 0 && lastComma >= 0 -> {
                decimalSeparator = if (lastDot > lastComma) '.' else ','
                groupingSeparator = if (lastDot > lastComma) ',' else '.'
            }

            lastDot >= 0 -> {
                val isFraction = introducesFraction(digits, '.')
                decimalSeparator = if (isFraction) '.' else null
                groupingSeparator = if (isFraction) null else '.'
            }

            lastComma >= 0 -> {
                val isFraction = introducesFraction(digits, ',')
                decimalSeparator = if (isFraction) ',' else null
                groupingSeparator = if (isFraction) null else ','
            }

            else -> {
                decimalSeparator = null
                groupingSeparator = null
            }
        }

        val integerPart: String
        val fractionPart: String
        if (decimalSeparator != null) {
            val index = digits.lastIndexOf(decimalSeparator)
            integerPart = digits.substring(0, index)
            fractionPart = digits.substring(index + 1)
            if (fractionPart.isEmpty() || fractionPart.length > MAX_FRACTION_DIGITS) return null
            if (!fractionPart.all(Char::isDigit)) return null
        } else {
            integerPart = digits
            fractionPart = ""
        }

        val wholeDigits = validateGrouping(integerPart, groupingSeparator) ?: return null
        if (wholeDigits.isEmpty() && fractionPart.isEmpty()) return null

        val text = buildString {
            append(wholeDigits.ifEmpty { "0" })
            if (fractionPart.isNotEmpty()) {
                append('.')
                append(fractionPart)
            }
        }
        return runCatching { BigDecimal(text) }.getOrNull()
    }

    /**
     * With only one kind of separator present, decides whether it groups thousands or
     * introduces the fraction. `1,234` is one thousand; `12,34` is twelve and change.
     */
    private fun introducesFraction(digits: String, separator: Char): Boolean {
        if (digits.count { it == separator } > 1) return false
        val trailing = digits.length - digits.indexOf(separator) - 1
        return trailing in 1..2
    }

    /**
     * Returns the bare digits of the whole-number part, or null when the grouping is
     * not a plausible way to write a number. This is what stops `1.2.3.4` from being
     * read as one thousand two hundred and thirty-four.
     */
    private fun validateGrouping(integerPart: String, separator: Char?): String? {
        if (separator == null) {
            return if (integerPart.all(Char::isDigit)) integerPart else null
        }
        val groups = integerPart.split(separator)
        if (groups.any { group -> group.isEmpty() || !group.all(Char::isDigit) }) return null
        if (groups.size > 1) {
            if (groups.first().length !in 1..GROUP_SIZE) return null
            if (groups.drop(1).any { it.length != GROUP_SIZE }) return null
        }
        return groups.joinToString("")
    }

    private companion object {
        const val GROUP_SIZE = 3

        // Fuel and some unit prices are quoted to three decimal places.
        const val MAX_FRACTION_DIGITS = 3

        val CODE_PATTERN = Regex("\\b[A-Z]{3}\\b")

        // Ordered so that multi-character symbols are tested before "$".
        val SYMBOLS: List<Pair<String, CurrencyCode>> = listOf(
            "R$" to CurrencyCode("BRL"),
            "CA$" to CurrencyCode("CAD"),
            "A$" to CurrencyCode("AUD"),
            "NZ$" to CurrencyCode("NZD"),
            "€" to CurrencyCode.EUR,
            "£" to CurrencyCode.GBP,
            "¥" to CurrencyCode("JPY"),
            "₹" to CurrencyCode("INR"),
            "₩" to CurrencyCode("KRW"),
            "₪" to CurrencyCode("ILS"),
            "₺" to CurrencyCode("TRY"),
            "zł" to CurrencyCode("PLN"),
            "$" to CurrencyCode.USD,
        )
    }
}
