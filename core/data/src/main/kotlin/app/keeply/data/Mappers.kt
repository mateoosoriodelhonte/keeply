package app.keeply.data

import app.keeply.domain.Category
import app.keeply.domain.CategoryId
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.DocumentText
import app.keeply.domain.FieldConfidence
import app.keeply.domain.FieldSource
import app.keeply.domain.LineItem
import app.keeply.domain.Merchant
import app.keeply.domain.MerchantId
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindow
import app.keeply.domain.StoredDocument
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import java.time.Instant
import java.time.LocalDate
import app.keeply.data.sql.Category as CategoryRow
import app.keeply.data.sql.Document as DocumentRow
import app.keeply.data.sql.Document_text as DocumentTextRow
import app.keeply.data.sql.Line_item as LineItemRow
import app.keeply.data.sql.Merchant as MerchantRow
import app.keeply.data.sql.Purchase as PurchaseRow

/**
 * Translation between SQLite rows and the domain model.
 *
 * Sum types are stored as a discriminator column plus its payload columns rather
 * than as serialised blobs, so the database stays queryable and a person looking at
 * it with any SQLite tool can see what Keeply recorded.
 *
 * Anything unreadable maps back to "unknown" rather than throwing. A single corrupt
 * row should cost a person one purchase's details, not access to their whole library.
 */
internal object Mappers {

    // --- Purchase ------------------------------------------------------------

    fun toPurchase(
        row: PurchaseRow,
        tags: List<String>,
        lineItems: List<LineItem>,
        manualIds: List<DocumentId>,
        warrantyDocIds: List<DocumentId>,
    ): Purchase {
        val currency = row.price_currency?.let { CurrencyCode.orNull(it) }
        return Purchase(
            id = PurchaseId(row.id),
            productName = row.product_name,
            merchantId = row.merchant_id?.let(::MerchantId),
            merchantName = row.merchant_name,
            purchaseDate = row.purchase_date?.toLocalDateOrNull(),
            price = money(row.price_minor, currency),
            tax = money(row.tax_minor, currency),
            categoryId = row.category_id?.let(::CategoryId),
            receiptDocumentId = row.receipt_document_id?.let(::DocumentId),
            receiptNumber = row.receipt_number,
            returnWindow = ReturnWindow(
                policy = returnPolicy(row.return_policy_kind, row.return_policy_days, row.return_policy_until),
                source = enumOrDefault(row.return_source, ReturnPolicySource.NONE),
                deadline = row.return_deadline?.toLocalDateOrNull(),
            ),
            warranty = Warranty(
                term = warrantyTerm(row.warranty_term_kind, row.warranty_term_value, row.warranty_term_until),
                provenance = enumOrDefault(row.warranty_provenance, WarrantyProvenance.SUGGESTED),
                startDate = row.warranty_start?.toLocalDateOrNull(),
                endDate = row.warranty_end?.toLocalDateOrNull(),
                notes = row.warranty_notes,
            ),
            paymentMethodLabel = row.payment_method_label,
            notes = row.notes,
            tags = tags,
            productPhotoId = row.product_photo_id?.let(::DocumentId),
            manualDocumentIds = manualIds,
            warrantyDocumentIds = warrantyDocIds,
            serialNumber = row.serial_number,
            lineItems = lineItems,
            isArchived = row.is_archived != 0L,
            isDemoData = row.is_demo != 0L,
            createdAt = Instant.ofEpochMilli(row.created_at),
            updatedAt = Instant.ofEpochMilli(row.updated_at),
        )
    }

    fun returnPolicyColumns(policy: ReturnPolicy): Triple<String, Long?, String?> = when (policy) {
        is ReturnPolicy.Days -> Triple("DAYS", policy.days.toLong(), null)
        is ReturnPolicy.Until -> Triple("UNTIL", null, policy.date.toString())
        ReturnPolicy.Unknown -> Triple("UNKNOWN", null, null)
    }

    fun warrantyTermColumns(term: WarrantyTerm): Triple<String, Long?, String?> = when (term) {
        is WarrantyTerm.Months -> Triple("MONTHS", term.months.toLong(), null)
        is WarrantyTerm.Days -> Triple("DAYS", term.days.toLong(), null)
        is WarrantyTerm.Until -> Triple("UNTIL", null, term.date.toString())
        WarrantyTerm.Lifetime -> Triple("LIFETIME", null, null)
        WarrantyTerm.Unknown -> Triple("UNKNOWN", null, null)
    }

    private fun returnPolicy(kind: String, days: Long?, until: String?): ReturnPolicy = when (kind) {
        "DAYS" -> days?.toInt()?.let { runCatching { ReturnPolicy.Days(it) }.getOrNull() } ?: ReturnPolicy.Unknown
        "UNTIL" -> until?.toLocalDateOrNull()?.let(ReturnPolicy::Until) ?: ReturnPolicy.Unknown
        else -> ReturnPolicy.Unknown
    }

    private fun warrantyTerm(kind: String, value: Long?, until: String?): WarrantyTerm = when (kind) {
        "MONTHS" -> value?.toInt()?.let { runCatching { WarrantyTerm.Months(it) }.getOrNull() } ?: WarrantyTerm.Unknown
        "DAYS" -> value?.toInt()?.let { runCatching { WarrantyTerm.Days(it) }.getOrNull() } ?: WarrantyTerm.Unknown
        "UNTIL" -> until?.toLocalDateOrNull()?.let(WarrantyTerm::Until) ?: WarrantyTerm.Unknown
        "LIFETIME" -> WarrantyTerm.Lifetime
        else -> WarrantyTerm.Unknown
    }

    // --- Line items ----------------------------------------------------------

    fun toLineItem(row: LineItemRow, currency: CurrencyCode?): LineItem = LineItem(
        description = row.description,
        quantity = row.quantity?.toInt()?.takeIf { it > 0 },
        unitPrice = money(row.unit_price_minor, currency),
        totalPrice = money(row.total_price_minor, currency),
        confidence = enumOrDefault(row.confidence, FieldConfidence.UNCERTAIN),
    )

    // --- Merchant ------------------------------------------------------------

    fun toMerchant(row: MerchantRow): Merchant = Merchant(
        id = MerchantId(row.id),
        name = row.name,
        matchKey = row.match_key,
        aliases = row.aliases.lines().filter(String::isNotBlank),
        defaultReturnPolicy = returnPolicy(
            row.return_policy_kind ?: "UNKNOWN",
            row.return_policy_days,
            row.return_policy_until,
        ),
        notes = row.notes,
    )

    // --- Category ------------------------------------------------------------

    fun toCategory(row: CategoryRow): Category = Category(
        id = CategoryId(row.id),
        name = row.name,
        icon = row.icon,
        isBuiltIn = row.is_built_in != 0L,
        sortOrder = row.sort_order.toInt(),
        suggestedWarranty = warrantyTerm(
            row.suggested_warranty_kind ?: "UNKNOWN",
            row.suggested_warranty_value,
            null,
        ),
    )

    fun suggestedWarrantyColumns(term: WarrantyTerm): Pair<String?, Long?> = when (term) {
        is WarrantyTerm.Months -> "MONTHS" to term.months.toLong()
        is WarrantyTerm.Days -> "DAYS" to term.days.toLong()
        WarrantyTerm.Lifetime -> "LIFETIME" to null
        else -> null to null
    }

    // --- Documents -----------------------------------------------------------

    fun toDocument(row: DocumentRow): StoredDocument = StoredDocument(
        id = DocumentId(row.id),
        kind = enumOrDefault(row.kind, DocumentKind.RECEIPT),
        format = enumOrDefault(row.format, DocumentFormat.PDF),
        relativePath = row.relative_path,
        byteSize = row.byte_size,
        sha256 = row.sha256,
        importedAt = Instant.ofEpochMilli(row.imported_at),
        originalFileName = row.original_file_name,
        pageCount = row.page_count?.toInt(),
    )

    fun toDocumentText(row: DocumentTextRow): DocumentText = DocumentText(
        documentId = DocumentId(row.document_id),
        text = row.text,
        source = enumOrDefault(row.source, FieldSource.OCR),
        meanConfidence = row.mean_confidence?.coerceIn(0.0, 100.0),
        extractedAt = Instant.ofEpochMilli(row.extracted_at),
        engine = row.engine,
        durationMillis = row.duration_millis,
    )

    // --- Shared helpers ------------------------------------------------------

    private fun money(minor: Long?, currency: CurrencyCode?): Money? =
        if (minor != null && currency != null) Money(minor, currency) else null

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == raw } ?: fallback

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
}
