package app.keeply.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.keeply.data.sql.KeeplyDatabase
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentId
import app.keeply.domain.DuplicateFingerprint
import app.keeply.domain.Ids
import app.keeply.domain.Merchant
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** The role an attached document plays for a purchase. */
public enum class AttachmentRole {
    MANUAL,
    WARRANTY,
    EXTRA_PHOTO,
}

/**
 * Reading and writing purchases.
 *
 * List views load tags for the whole page in a single query rather than one query
 * per row, and skip line items entirely, because a library of several hundred
 * purchases should not cost several hundred round trips to draw.
 */
public class PurchaseRepository internal constructor(
    private val database: KeeplyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queries get() = database.purchaseQueries

    /** Every purchase that is not archived, with tags but without line items. */
    public fun all(): List<Purchase> = hydrateList(queries.selectAll().executeAsList())

    public fun recent(limit: Int): List<Purchase> = hydrateList(queries.selectRecent(limit.toLong()).executeAsList())

    /** Emits the library again whenever anything in it changes. */
    public fun observeAll(): Flow<List<Purchase>> = queries.selectAll().asFlow().mapToList(dispatcher).map { hydrateList(it) }

    /** One purchase in full, including line items and attached documents. */
    public fun get(id: PurchaseId): Purchase? {
        val row = queries.selectById(id.value).executeAsOneOrNull() ?: return null
        val currency = row.price_currency?.let { CurrencyCode.orNull(it) }
        val attachments = queries.documentsFor(id.value).executeAsList()
        return Mappers.toPurchase(
            row = row,
            tags = queries.tagsFor(id.value).executeAsList(),
            lineItems = queries.lineItemsFor(id.value).executeAsList()
                .map { Mappers.toLineItem(it, currency) },
            manualIds = attachments.filter { it.role == AttachmentRole.MANUAL.name }
                .map { DocumentId(it.document_id) },
            warrantyDocIds = attachments.filter { it.role == AttachmentRole.WARRANTY.name }
                .map { DocumentId(it.document_id) },
        )
    }

    public fun byIds(ids: Collection<PurchaseId>): List<Purchase> {
        if (ids.isEmpty()) return emptyList()
        return hydrateList(queries.selectByIds(ids.map { it.value }).executeAsList())
    }

    public fun returnsClosingBetween(from: LocalDate, to: LocalDate): List<Purchase> =
        hydrateList(queries.selectReturnsClosingBy(from.toString(), to.toString()).executeAsList())

    public fun warrantiesEndingBetween(from: LocalDate, to: LocalDate): List<Purchase> =
        hydrateList(queries.selectWarrantiesEndingBy(from.toString(), to.toString()).executeAsList())

    /** Everything needed to spot a duplicate, in one query. */
    public fun fingerprints(): List<DuplicateFingerprint> = queries.selectFingerprints().executeAsList().map { row ->
        val currency = row.price_currency?.let { CurrencyCode.orNull(it) }
        DuplicateFingerprint(
            purchaseId = PurchaseId(row.id),
            fileSha256 = row.sha256,
            merchantKey = row.merchant_name?.let(Merchant::normaliseName),
            purchaseDate = row.purchase_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            total = if (row.price_minor != null && currency != null) Money(row.price_minor, currency) else null,
            receiptNumber = row.receipt_number,
        )
    }

    /**
     * Writes a purchase and everything hanging off it in one transaction, then
     * refreshes its search index entry.
     */
    public fun save(purchase: Purchase, receiptText: String? = null) {
        database.transaction {
            val (returnKind, returnDays, returnUntil) = Mappers.returnPolicyColumns(purchase.returnWindow.policy)
            val (warrantyKind, warrantyValue, warrantyUntil) = Mappers.warrantyTermColumns(purchase.warranty.term)

            queries.upsert(
                id = purchase.id.value,
                product_name = purchase.productName,
                merchant_id = purchase.merchantId?.value,
                merchant_name = purchase.merchantName,
                purchase_date = purchase.purchaseDate?.toString(),
                price_minor = purchase.price?.amountMinor,
                price_currency = purchase.price?.currency?.code,
                tax_minor = purchase.tax?.amountMinor,
                category_id = purchase.categoryId?.value,
                receipt_document_id = purchase.receiptDocumentId?.value,
                receipt_number = purchase.receiptNumber,
                return_policy_kind = returnKind,
                return_policy_days = returnDays,
                return_policy_until = returnUntil,
                return_source = purchase.returnWindow.source.name,
                return_deadline = purchase.returnWindow.deadline?.toString(),
                warranty_term_kind = warrantyKind,
                warranty_term_value = warrantyValue,
                warranty_term_until = warrantyUntil,
                warranty_provenance = purchase.warranty.provenance.name,
                warranty_start = purchase.warranty.startDate?.toString(),
                warranty_end = purchase.warranty.endDate?.toString(),
                warranty_notes = purchase.warranty.notes,
                payment_method_label = purchase.paymentMethodLabel,
                notes = purchase.notes,
                serial_number = purchase.serialNumber,
                product_photo_id = purchase.productPhotoId?.value,
                is_archived = if (purchase.isArchived) 1L else 0L,
                is_demo = if (purchase.isDemoData) 1L else 0L,
                created_at = purchase.createdAt.toEpochMilli(),
                updated_at = purchase.updatedAt.toEpochMilli(),
            )

            queries.deleteTags(purchase.id.value)
            purchase.tags.map(String::trim).filter(String::isNotEmpty).distinct().forEach { tag ->
                queries.insertTag(purchase.id.value, tag)
            }

            queries.deleteLineItems(purchase.id.value)
            purchase.lineItems.forEachIndexed { index, item ->
                queries.insertLineItem(
                    id = Ids.generate(),
                    purchase_id = purchase.id.value,
                    position = index.toLong(),
                    description = item.description,
                    quantity = item.quantity?.toLong(),
                    unit_price_minor = item.unitPrice?.amountMinor,
                    total_price_minor = item.totalPrice?.amountMinor,
                    confidence = item.confidence.name,
                )
            }

            queries.deleteDocuments(purchase.id.value)
            purchase.manualDocumentIds.forEach {
                queries.insertDocument(purchase.id.value, it.value, AttachmentRole.MANUAL.name)
            }
            purchase.warrantyDocumentIds.forEach {
                queries.insertDocument(purchase.id.value, it.value, AttachmentRole.WARRANTY.name)
            }

            SearchRepository(database).reindex(purchase, receiptText)
        }
    }

    public fun setArchived(id: PurchaseId, archived: Boolean, now: Instant = Instant.now()) {
        queries.setArchived(if (archived) 1L else 0L, now.toEpochMilli(), id.value)
    }

    public fun delete(id: PurchaseId) {
        database.transaction {
            SearchRepository(database).remove(id)
            queries.deleteById(id.value)
        }
    }

    public fun deleteDemoData() {
        database.transaction {
            val demoIds = queries.selectAll().executeAsList().filter { it.is_demo != 0L }.map { PurchaseId(it.id) }
            val search = SearchRepository(database)
            demoIds.forEach(search::remove)
            queries.deleteDemoData()
        }
    }

    public fun count(): Long = queries.countAll().executeAsOne()

    public fun countPurchasedBetween(from: LocalDate, to: LocalDate): Long =
        queries.countPurchasedBetween(from.toString(), to.toString()).executeAsOne()

    public fun topMerchants(limit: Int): List<Pair<String, Long>> = queries.topMerchants(limit.toLong())
        .executeAsList()
        .map { row -> row.merchant_name to row.purchase_count }

    public fun countByCategory(): Map<String?, Long> =
        queries.countByCategory().executeAsList().associate { it.category_id to it.purchase_count }

    public fun allTags(): List<String> = queries.allTags().executeAsList()

    public fun attach(purchaseId: PurchaseId, documentId: DocumentId, role: AttachmentRole) {
        queries.insertDocument(purchaseId.value, documentId.value, role.name)
    }

    public fun detach(purchaseId: PurchaseId, documentId: DocumentId, role: AttachmentRole) {
        queries.deleteDocumentLink(purchaseId.value, documentId.value, role.name)
    }

    public fun attachments(purchaseId: PurchaseId): Map<AttachmentRole, List<DocumentId>> =
        queries.documentsFor(purchaseId.value).executeAsList()
            .mapNotNull { row ->
                AttachmentRole.entries.firstOrNull { it.name == row.role }?.let { it to DocumentId(row.document_id) }
            }
            .groupBy({ it.first }, { it.second })

    private fun hydrateList(rows: List<app.keeply.data.sql.Purchase>): List<Purchase> {
        if (rows.isEmpty()) return emptyList()
        val tagsById = queries.tagsForPurchases(rows.map { it.id }).executeAsList()
            .groupBy({ it.purchase_id }, { it.tag })
        return rows.map { row ->
            Mappers.toPurchase(
                row = row,
                tags = tagsById[row.id].orEmpty(),
                lineItems = emptyList(),
                manualIds = emptyList(),
                warrantyDocIds = emptyList(),
            )
        }
    }
}
