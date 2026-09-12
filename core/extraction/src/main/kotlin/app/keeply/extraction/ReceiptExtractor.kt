package app.keeply.extraction

import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentId
import app.keeply.domain.Field
import app.keeply.domain.FieldConfidence
import app.keeply.domain.FieldSource
import app.keeply.domain.Merchant
import app.keeply.domain.PurchaseDraft
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.WarrantyTerm
import java.time.LocalDate

/** The text to read, and what Keeply already knows that might help. */
public data class ExtractionInput(
    val text: String,
    val source: FieldSource,
    val documentId: DocumentId? = null,
    /** Mean OCR confidence, when the text came from OCR. Null for embedded PDF text. */
    val meanOcrConfidence: Double? = null,
    val knownMerchants: List<Merchant> = emptyList(),
    val defaultCurrency: CurrencyCode = CurrencyCode.USD,
    val preferDayFirstDates: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

/** One decision the extractor made, for the debug view and for the review screen. */
public data class ExtractionNote(val field: String, val outcome: String, val rule: String, val evidence: String? = null)

public data class ExtractionOutcome(val draft: PurchaseDraft, val notes: List<ExtractionNote>) {
    /** Fields the extractor could not read at all, named for the review screen. */
    public val missing: List<String> get() = notes.filter { it.outcome == NOT_FOUND }.map { it.field }

    public companion object {
        public const val NOT_FOUND: String = "not found"
    }
}

/**
 * Reads a receipt without an LLM.
 *
 * This is Keeply's product, not a fallback for one. Everything it produces can be
 * traced to a rule and a line of text, which is what makes the review screen worth
 * looking at and the debug view worth having.
 *
 * Two rules run through all of it. Nothing is invented: a field that could not be
 * read is left empty rather than filled with a plausible guess. And nothing
 * overstates itself: a value with no corroboration is marked uncertain so the
 * person is asked, even when it is probably right.
 */
public class ReceiptExtractor(
    private val merchantFinder: MerchantFinder = MerchantFinder(),
    private val receiptNumberFinder: ReceiptNumberFinder = ReceiptNumberFinder(),
) {
    public fun extract(input: ExtractionInput): ExtractionOutcome {
        val notes = mutableListOf<ExtractionNote>()
        val text = input.text
        val currency = detectCurrency(text, input.defaultCurrency)
        val scanner = MoneyScanner(currency)
        val dateParser = ReceiptDateParser(input.preferDayFirstDates, today = { input.today })

        // Text that came from a PDF's own text layer was never guessed at, so a
        // reading from it starts out stronger than the same reading from a photo.
        val exact = input.source == FieldSource.PDF_TEXT

        val merchant = merchantFinder.find(text, input.knownMerchants)
        notes += note("Store", merchant?.name, merchant?.rule, merchant?.evidence)

        val date = dateParser.findPurchaseDate(text)
        notes += note(
            "Purchase date",
            date?.date?.toString(),
            when {
                date == null -> null
                date.ambiguous -> "read as ${date.raw}, but the day and month could be either way round"
                date.repaired -> "read as ${date.raw}, with characters OCR confused for digits corrected"
                else -> "read as ${date.raw}"
            },
            date?.raw,
        )

        val total = TotalFinder(scanner).find(text)
        notes += note("Total", total?.total?.toPlainString(), total?.rule, total?.evidence)

        val receiptNumber = receiptNumberFinder.find(text)
        notes += note("Receipt number", receiptNumber?.number, receiptNumber?.rule, receiptNumber?.evidence)

        val printedPolicy = ReturnPolicyFinder { input.today }.find(text)
        notes += note(
            "Return window",
            (printedPolicy?.policy as? ReturnPolicy.Days)?.days?.let { "$it days" },
            printedPolicy?.let { "printed on the receipt" },
            printedPolicy?.evidence,
        )

        val items = LineItemParser(scanner).parse(text, currency)
        notes += ExtractionNote(
            field = "Items",
            outcome = if (items.isEmpty()) ExtractionOutcome.NOT_FOUND else "${items.size} found",
            rule = "lines carrying both a description and an amount",
        )

        val savedRule = merchant?.matchedMerchant?.defaultReturnPolicy ?: ReturnPolicy.Unknown
        val (policy, policySource) = when {
            printedPolicy != null -> printedPolicy.policy to ReturnPolicySource.PRINTED_ON_RECEIPT
            savedRule != ReturnPolicy.Unknown -> savedRule to ReturnPolicySource.SAVED_MERCHANT_RULE
            else -> ReturnPolicy.Unknown to ReturnPolicySource.NONE
        }

        val draft = PurchaseDraft(
            sourceDocumentId = input.documentId,
            productName = suggestProductName(items)?.let {
                Field(it, FieldConfidence.UNCERTAIN, input.source, evidence = it)
            },
            merchantName = merchant?.let {
                Field(it.name, strengthen(it.confidence, exact), input.source, it.evidence)
            },
            matchedMerchantId = merchant?.matchedMerchant?.id,
            purchaseDate = date?.let {
                val confidence = when {
                    it.ambiguous -> FieldConfidence.UNCERTAIN
                    it.repaired -> FieldConfidence.UNCERTAIN
                    else -> strengthen(FieldConfidence.CONFIDENT, exact)
                }
                Field(it.date, confidence, input.source, it.raw)
            },
            total = total?.let { Field(it.total, strengthen(it.confidence, exact), input.source, it.evidence) },
            subtotal = total?.subtotal?.let { Field(it, FieldConfidence.UNCERTAIN, input.source) },
            tax = total?.tax?.let { Field(it, FieldConfidence.UNCERTAIN, input.source) },
            currency = Field(currency, FieldConfidence.CONFIDENT, input.source),
            receiptNumber = receiptNumber?.let {
                Field(it.number, strengthen(it.confidence, exact), input.source, it.evidence)
            },
            lineItems = items,
            suggestedReturnPolicy = policy,
            suggestedReturnSource = policySource,
            suggestedWarranty = WarrantyTerm.Unknown,
        )

        return ExtractionOutcome(draft, notes)
    }

    /**
     * A reading from a PDF's own text layer is not a guess, so a rule that would
     * otherwise be uncertain can be trusted. A reading from OCR never is.
     */
    private fun strengthen(confidence: FieldConfidence, exact: Boolean): FieldConfidence =
        if (exact && confidence == FieldConfidence.UNCERTAIN) FieldConfidence.CONFIDENT else confidence

    /**
     * Offers the most expensive item as the product name.
     *
     * A four-item grocery receipt is not one purchase with a name, and Keeply does
     * not pretend otherwise: this is a suggestion, always marked uncertain, and the
     * review screen expects it to be replaced.
     */
    private fun suggestProductName(items: List<app.keeply.domain.LineItem>): String? =
        items.maxByOrNull { it.totalPrice?.amountMinor ?: 0L }
            ?.description
            ?.takeIf { it.length >= MIN_NAME_LENGTH }
            ?.let { tidyProductName(it) }

    private fun tidyProductName(raw: String): String {
        val cleaned = raw.trim().trim('-', '*', '.', ':')
        if (cleaned.any { it.isLowerCase() }) return cleaned
        return cleaned.split(' ').joinToString(" ") { word ->
            if (word.length <= SHORT_WORD || word.any(Char::isDigit)) {
                word
            } else {
                word.lowercase().replaceFirstChar(Char::uppercaseChar)
            }
        }
    }

    /** The currency actually printed on the receipt, falling back to the person's default. */
    private fun detectCurrency(text: String, fallback: CurrencyCode): CurrencyCode {
        CODE.findAll(text).forEach { match ->
            CurrencyCode.orNull(match.value)?.let { return it }
        }
        SYMBOLS.forEach { (symbol, code) -> if (text.contains(symbol)) return code }
        return fallback
    }

    private fun note(field: String, value: String?, rule: String?, evidence: String?): ExtractionNote = ExtractionNote(
        field = field,
        outcome = value ?: ExtractionOutcome.NOT_FOUND,
        rule = rule ?: "no rule matched",
        evidence = evidence,
    )

    private companion object {
        const val MIN_NAME_LENGTH = 3
        const val SHORT_WORD = 3

        val CODE = Regex("""\b(?:USD|EUR|GBP|CAD|AUD|JPY|CHF|SEK|NZD|INR)\b""")
        val SYMBOLS = listOf(
            "€" to CurrencyCode.EUR,
            "£" to CurrencyCode.GBP,
            "¥" to CurrencyCode("JPY"),
            "₹" to CurrencyCode("INR"),
            "$" to CurrencyCode.USD,
        )
    }
}
