package app.keeply.backup

import app.keeply.domain.Category
import app.keeply.domain.Purchase
import app.keeply.domain.ReturnStatus
import app.keeply.domain.WarrantyStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** One purchase, flattened for export. */
@Serializable
public data class ExportedPurchase(
    val id: String,
    val product: String,
    val merchant: String? = null,
    @SerialName("purchase_date")
    val purchaseDate: String? = null,
    val price: String? = null,
    val tax: String? = null,
    val currency: String? = null,
    val category: String? = null,
    @SerialName("receipt_number")
    val receiptNumber: String? = null,
    @SerialName("return_deadline")
    val returnDeadline: String? = null,
    @SerialName("return_status")
    val returnStatus: String,
    @SerialName("return_source")
    val returnSource: String,
    @SerialName("warranty_ends")
    val warrantyEnds: String? = null,
    @SerialName("warranty_status")
    val warrantyStatus: String,
    @SerialName("warranty_source")
    val warrantySource: String,
    @SerialName("payment_method")
    val paymentMethod: String? = null,
    @SerialName("serial_number")
    val serialNumber: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val archived: Boolean = false,
)

@Serializable
public data class ExportedLibrary(
    @SerialName("exported_at")
    val exportedAt: String,
    @SerialName("app_version")
    val appVersion: String,
    val purchases: List<ExportedPurchase>,
)

/**
 * Turns a library into CSV and JSON.
 *
 * Keeply keeps a person's receipts on their own machine, which means nothing
 * stops them leaving. Export exists so that promise is real rather than
 * rhetorical: the CSV opens in any spreadsheet, and the JSON has every field.
 */
public object PurchaseExport {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    public fun toRows(
        purchases: List<Purchase>,
        categories: List<Category> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): List<ExportedPurchase> {
        val categoriesById = categories.associateBy { it.id }
        return purchases.map { purchase ->
            ExportedPurchase(
                id = purchase.id.value,
                product = purchase.productName,
                merchant = purchase.merchantName,
                purchaseDate = purchase.purchaseDate?.toString(),
                price = purchase.price?.toPlainString(),
                tax = purchase.tax?.toPlainString(),
                // A purchase can have tax recorded without a price, so the currency
                // column falls back rather than coming out blank beside a number.
                currency = (purchase.price ?: purchase.tax)?.currency?.code,
                category = purchase.categoryId?.let { categoriesById[it]?.name },
                receiptNumber = purchase.receiptNumber,
                returnDeadline = purchase.returnWindow.deadline?.toString(),
                returnStatus = describe(purchase.returnStatusOn(today)),
                returnSource = purchase.returnWindow.source.name.lowercase().replace('_', ' '),
                warrantyEnds = purchase.warranty.endDate?.toString(),
                warrantyStatus = describe(purchase.warrantyStatusOn(today)),
                warrantySource = purchase.warranty.provenance.name.lowercase().replace('_', ' '),
                paymentMethod = purchase.paymentMethodLabel,
                serialNumber = purchase.serialNumber,
                notes = purchase.notes,
                tags = purchase.tags,
                archived = purchase.isArchived,
            )
        }
    }

    public fun toJson(rows: List<ExportedPurchase>, appVersion: String): String = json.encodeToString(
        ExportedLibrary.serializer(),
        ExportedLibrary(java.time.Instant.now().toString(), appVersion, rows),
    )

    public fun toCsv(rows: List<ExportedPurchase>): String = buildString {
        appendLine(HEADERS.joinToString(","))
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.id, row.product, row.merchant, row.purchaseDate, row.price, row.tax,
                    row.currency, row.category, row.receiptNumber, row.returnDeadline,
                    row.returnStatus, row.returnSource, row.warrantyEnds, row.warrantyStatus,
                    row.warrantySource, row.paymentMethod, row.serialNumber, row.notes,
                    row.tags.joinToString(" "), row.archived.toString(),
                ).joinToString(",") { escape(it) },
            )
        }
    }

    /**
     * Escapes a CSV cell, and disarms it.
     *
     * A spreadsheet treats a cell beginning with `=`, `+`, `-` or `@` as a formula.
     * A product name typed as `=cmd|'/c calc'!A1` would then run when the file is
     * opened, which is a real attack against exported data. A leading apostrophe
     * makes the cell text and is invisible in every spreadsheet application.
     */
    private fun escape(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val disarmed = if (value.first() in FORMULA_STARTERS) "'$value" else value
        val needsQuotes = disarmed.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = disarmed.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun describe(status: ReturnStatus): String = when (status) {
        ReturnStatus.RETURNABLE -> "returnable"
        ReturnStatus.ENDS_SOON -> "ends soon"
        ReturnStatus.ENDED -> "ended"
        ReturnStatus.UNKNOWN -> "unknown"
    }

    private fun describe(status: WarrantyStatus): String = when (status) {
        WarrantyStatus.ACTIVE -> "active"
        WarrantyStatus.EXPIRING_SOON -> "expiring soon"
        WarrantyStatus.EXPIRED -> "expired"
        WarrantyStatus.LIFETIME -> "lifetime"
        WarrantyStatus.UNKNOWN -> "unknown"
    }

    private val FORMULA_STARTERS = setOf('=', '+', '-', '@', '\t', '\r')

    private val HEADERS = listOf(
        "id", "product", "merchant", "purchase_date", "price", "tax", "currency",
        "category", "receipt_number", "return_deadline", "return_status", "return_source",
        "warranty_ends", "warranty_status", "warranty_source", "payment_method",
        "serial_number", "notes", "tags", "archived",
    )
}
