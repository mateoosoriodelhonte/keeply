package app.keeply.fixtures

import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * The shape a generated receipt takes on paper.
 *
 * Real tills print in very different ways, and an extractor that only handles one
 * of them is not worth much. These are the arrangements Keeply is tested against.
 */
public enum class ReceiptLayout {
    /** Narrow thermal roll: centred header, one line per item, total at the bottom. */
    CLASSIC_TILL,

    /** Aligned columns for quantity, description and price. */
    COLUMNAR,

    /** Cramped, abbreviated, no blank lines. The hardest to read. */
    COMPACT,

    /** A wide printed invoice with labelled fields rather than a till roll. */
    INVOICE,
}

public data class ReceiptItem(val description: String, val quantity: Int, val unitPriceMinor: Long) {
    val totalMinor: Long get() = unitPriceMinor * quantity
}

/**
 * A receipt to generate. Entirely invented: these are not imitations of any real
 * company's documents, and no real receipt is ever committed to this repository.
 */
public data class ReceiptSpec(
    val merchantName: String,
    val addressLines: List<String>,
    val purchaseDate: LocalDate,
    val time: LocalTime,
    val items: List<ReceiptItem>,
    val currency: CurrencyCode,
    /** Tax rate in basis points: 875 means 8.75%. */
    val taxRateBasisPoints: Int,
    val receiptNumber: String,
    val paymentLabel: String,
    val layout: ReceiptLayout,
    /** A returns line printed on the receipt, when this shop prints one. */
    val returnPolicyLine: String? = null,
    /** Some receipts print tax already included in item prices rather than added. */
    val taxIncludedInPrices: Boolean = false,
    /** Plenty of tills group thousands: $1,019.72 rather than $1019.72. */
    val groupsThousands: Boolean = false,
) {
    val subtotalMinor: Long
        get() = if (taxIncludedInPrices) itemsTotalMinor - taxMinor else itemsTotalMinor

    val taxMinor: Long
        get() = if (taxIncludedInPrices) {
            // Tax already inside the price: back it out rather than adding it on.
            Math.round(itemsTotalMinor * taxRateBasisPoints.toDouble() / (10_000.0 + taxRateBasisPoints))
        } else {
            Math.round(itemsTotalMinor * taxRateBasisPoints.toDouble() / 10_000.0)
        }

    val totalMinor: Long
        get() = if (taxIncludedInPrices) itemsTotalMinor else itemsTotalMinor + taxMinor

    val subtotal: Money get() = Money(subtotalMinor, currency)
    val tax: Money get() = Money(taxMinor, currency)
    val total: Money get() = Money(totalMinor, currency)

    private val itemsTotalMinor: Long get() = items.sumOf { it.totalMinor }

    /** What a correct extraction should produce. Tests compare against this. */
    public fun expectations(): ReceiptExpectations = ReceiptExpectations(
        merchantName = merchantName,
        purchaseDate = purchaseDate,
        total = total,
        subtotal = subtotal,
        tax = tax,
        currency = currency,
        receiptNumber = receiptNumber,
        itemCount = items.size,
    )
}

/** The truth about a generated receipt, used to score extraction. */
public data class ReceiptExpectations(
    val merchantName: String,
    val purchaseDate: LocalDate,
    val total: Money,
    val subtotal: Money,
    val tax: Money,
    val currency: CurrencyCode,
    val receiptNumber: String,
    val itemCount: Int,
)
