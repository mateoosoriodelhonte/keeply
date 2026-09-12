package app.keeply.fixtures

import app.keeply.domain.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lays a receipt out as plain text, the way a till would print it.
 *
 * This is the ground truth for extraction: the same text, rendered to an image and
 * read back by OCR, should produce the same fields. Comparing extraction against
 * this text isolates parsing bugs from OCR noise.
 */
public object ReceiptTextRenderer {

    public fun render(spec: ReceiptSpec): String = when (spec.layout) {
        ReceiptLayout.CLASSIC_TILL -> classicTill(spec)
        ReceiptLayout.COLUMNAR -> columnar(spec)
        ReceiptLayout.COMPACT -> compact(spec)
        ReceiptLayout.INVOICE -> invoice(spec)
    }

    private const val NARROW_WIDTH = 40
    private const val WIDE_WIDTH = 56

    private fun classicTill(spec: ReceiptSpec): String = buildString {
        val width = NARROW_WIDTH
        appendLine(centre(spec.merchantName.uppercase(Locale.ROOT), width))
        spec.addressLines.forEach { appendLine(centre(it, width)) }
        appendLine()
        appendLine("${spec.purchaseDate.format(SLASH_DATE)}  ${spec.time.format(CLOCK)}")
        appendLine("Receipt ${spec.receiptNumber}")
        appendLine("-".repeat(width))
        spec.items.forEach { item ->
            if (item.quantity > 1) {
                appendLine(item.description.take(width))
                appendLine(
                    leftRight(
                        "  ${item.quantity} @ ${amount(item.unitPriceMinor, spec)}",
                        amount(item.totalMinor, spec),
                        width,
                    ),
                )
            } else {
                appendLine(leftRight(item.description.take(width - 10), amount(item.totalMinor, spec), width))
            }
        }
        appendLine("-".repeat(width))
        if (!spec.taxIncludedInPrices) {
            appendLine(leftRight("SUBTOTAL", amount(spec.subtotalMinor, spec), width))
            appendLine(leftRight("TAX", amount(spec.taxMinor, spec), width))
        }
        appendLine(leftRight("TOTAL", amount(spec.totalMinor, spec), width))
        if (spec.taxIncludedInPrices) {
            appendLine(leftRight("INCLUDES TAX", amount(spec.taxMinor, spec), width))
        }
        appendLine()
        appendLine(spec.paymentLabel)
        spec.returnPolicyLine?.let { policy ->
            appendLine()
            wrap(policy, width).forEach { appendLine(centre(it, width)) }
        }
        appendLine()
        appendLine(centre("THANK YOU", width))
    }

    private fun columnar(spec: ReceiptSpec): String = buildString {
        val width = WIDE_WIDTH
        appendLine(spec.merchantName)
        appendLine(spec.addressLines.joinToString(", "))
        appendLine("=".repeat(width))
        appendLine("Date: ${spec.purchaseDate.format(ISO_DATE)}    Time: ${spec.time.format(CLOCK)}")
        appendLine("Transaction: ${spec.receiptNumber}")
        appendLine("=".repeat(width))
        appendLine(String.format("%-4s %-30s %10s %10s", "QTY", "ITEM", "PRICE", "AMOUNT"))
        appendLine("-".repeat(width))
        spec.items.forEach { item ->
            appendLine(
                String.format(
                    "%-4d %-30s %10s %10s",
                    item.quantity,
                    item.description.take(30),
                    amount(item.unitPriceMinor, spec),
                    amount(item.totalMinor, spec),
                ),
            )
        }
        appendLine("-".repeat(width))
        if (!spec.taxIncludedInPrices) {
            appendLine(leftRight("Subtotal", amount(spec.subtotalMinor, spec), width))
            appendLine(leftRight("Sales Tax (${taxRateLabel(spec)})", amount(spec.taxMinor, spec), width))
        }
        appendLine(leftRight("Amount Due", amount(spec.totalMinor, spec), width))
        appendLine()
        appendLine("Paid by ${spec.paymentLabel}")
        spec.returnPolicyLine?.let { appendLine(it) }
    }

    private fun compact(spec: ReceiptSpec): String = buildString {
        val width = NARROW_WIDTH
        appendLine(spec.merchantName.uppercase(Locale.ROOT))
        appendLine("${spec.purchaseDate.format(SHORT_DATE)} ${spec.time.format(CLOCK)} ${spec.receiptNumber}")
        spec.items.forEach { item ->
            val qty = if (item.quantity > 1) "${item.quantity}x " else ""
            appendLine(leftRight("$qty${abbreviate(item.description)}", amount(item.totalMinor, spec), width))
        }
        if (!spec.taxIncludedInPrices) {
            appendLine(leftRight("SUBTL", amount(spec.subtotalMinor, spec), width))
            appendLine(leftRight("TX", amount(spec.taxMinor, spec), width))
        }
        appendLine(leftRight("TTL", amount(spec.totalMinor, spec), width))
        appendLine(spec.paymentLabel)
    }

    private fun invoice(spec: ReceiptSpec): String = buildString {
        val width = WIDE_WIDTH
        appendLine(spec.merchantName)
        spec.addressLines.forEach { appendLine(it) }
        appendLine()
        appendLine("SALES RECEIPT")
        appendLine()
        appendLine("Invoice number : ${spec.receiptNumber}")
        appendLine("Date of sale   : ${spec.purchaseDate.format(LONG_DATE)}")
        appendLine("Payment method : ${spec.paymentLabel}")
        appendLine()
        appendLine("-".repeat(width))
        spec.items.forEach { item ->
            appendLine(
                leftRight(
                    "${item.description} (x${item.quantity})",
                    amount(item.totalMinor, spec),
                    width,
                ),
            )
        }
        appendLine("-".repeat(width))
        if (!spec.taxIncludedInPrices) {
            appendLine(leftRight("Subtotal", amount(spec.subtotalMinor, spec), width))
            appendLine(leftRight("Tax ${taxRateLabel(spec)}", amount(spec.taxMinor, spec), width))
        }
        appendLine(leftRight("Total", amount(spec.totalMinor, spec), width))
        spec.returnPolicyLine?.let {
            appendLine()
            appendLine(it)
        }
    }

    /**
     * How an amount appears on this particular receipt, including its currency
     * symbol and whether this till groups thousands. Tests use it to assert what
     * should be findable in the text.
     */
    public fun amountText(spec: ReceiptSpec, minor: Long): String = amount(minor, spec)

    private fun amount(minor: Long, spec: ReceiptSpec): String {
        val symbol = when (spec.currency.code) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> spec.currency.code + " "
        }
        val plain = Money(minor, spec.currency).toPlainString()
        return symbol + if (spec.groupsThousands) group(plain) else plain
    }

    /** Inserts thousands separators, which about half of real tills do. */
    private fun group(plain: String): String {
        val negative = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val whole = body.substringBefore('.')
        val fraction = body.substringAfter('.', "")
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return buildString {
            if (negative) append('-')
            append(grouped)
            if (fraction.isNotEmpty()) {
                append('.')
                append(fraction)
            }
        }
    }

    private fun taxRateLabel(spec: ReceiptSpec): String {
        val whole = spec.taxRateBasisPoints / 100
        val fraction = spec.taxRateBasisPoints % 100
        return if (fraction == 0) "$whole%" else "$whole.${fraction.toString().padStart(2, '0')}%"
    }

    private fun abbreviate(description: String): String = description.split(' ').joinToString(" ") { it.take(6) }.take(22)

    /** Wraps at word boundaries, the way a narrow till roll does. */
    private fun wrap(text: String, width: Int): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= width) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun centre(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text
    }

    private fun leftRight(left: String, right: String, width: Int): String {
        val space = (width - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(space) + right
    }

    private val SLASH_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.US)
    private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
}
