package app.keeply.services

import app.keeply.data.KeeplyStore
import app.keeply.documents.DataDirectory
import app.keeply.domain.CategoryId
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.DocumentText
import app.keeply.domain.FieldSource
import app.keeply.domain.Money
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.StoredDocument
import app.keeply.domain.WarrantyProvenance
import app.keeply.fixtures.CaptureConditions
import app.keeply.fixtures.DemoLibrary
import app.keeply.fixtures.ReceiptImageRenderer
import app.keeply.fixtures.ReceiptTextRenderer
import app.keeply.imaging.Thumbnailer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Fills Keeply with a small, believable library so somebody can see what it does
 * before importing anything of their own.
 *
 * Every purchase is generated, marked as demo data in the database, and removable
 * in one action. Nothing here is anybody's real receipt, and clearing it can never
 * touch anything real, because the delete is keyed on that flag.
 */
public class DemoData(
    private val store: KeeplyStore,
    private val directory: DataDirectory,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val log = LoggerFactory.getLogger(DemoData::class.java)

    public fun isInstalled(): Boolean = store.purchases.all().any { it.isDemoData }

    public fun install(today: LocalDate = LocalDate.now(clock)): Int {
        store.categories.installDefaultsIfEmpty()
        val purchaseService = PurchaseService(store, clock)
        var installed = 0

        DemoLibrary.purchases(today).forEach { demo ->
            val receiptText = ReceiptTextRenderer.render(demo.receipt)
            val document = writeReceipt(demo.receipt.merchantName, receiptText)

            store.documents.saveText(
                DocumentText(
                    documentId = document.id,
                    text = receiptText,
                    source = FieldSource.PDF_TEXT,
                    meanConfidence = null,
                    extractedAt = Instant.now(clock),
                    engine = "demo-data",
                    durationMillis = 0,
                ),
            )

            purchaseService.save(
                ReviewedPurchase(
                    productName = demo.productName,
                    merchantName = demo.receipt.merchantName,
                    purchaseDate = demo.receipt.purchaseDate,
                    price = Money(demo.receipt.totalMinor, demo.receipt.currency),
                    tax = Money(demo.receipt.taxMinor, demo.receipt.currency),
                    categoryId = CategoryId(demo.categoryId),
                    receiptNumber = demo.receipt.receiptNumber,
                    returnPolicy = demo.returnPolicy,
                    // Only a receipt that actually prints a returns line may claim
                    // one. The demo library holds Keeply to the same rule as a real
                    // import, because a screenshot that overstates provenance is
                    // still a claim about what the product does.
                    returnPolicySource = if (demo.receipt.returnPolicyLine != null) {
                        ReturnPolicySource.PRINTED_ON_RECEIPT
                    } else {
                        ReturnPolicySource.USER_ENTERED
                    },
                    warrantyTerm = demo.warranty,
                    warrantyProvenance = WarrantyProvenance.USER_ENTERED,
                    notes = demo.notes,
                    tags = demo.tags,
                    serialNumber = demo.serialNumber,
                    receiptDocumentId = document.id,
                    isDemoData = true,
                ),
                receiptText = receiptText,
            )
            installed++
        }
        log.info("Installed {} demo purchases", installed)
        return installed
    }

    /** Removes everything the demo added, and nothing else. */
    public fun clear() {
        store.purchases.deleteDemoData()
    }

    private fun writeReceipt(merchantName: String, text: String): StoredDocument {
        val image = ReceiptImageRenderer.render(text, CaptureConditions.CLEAN)
        val bytes = ReceiptImageRenderer.toPng(image)
        val id = DocumentId.new()
        val path = directory.allocate(DocumentKind.RECEIPT, id.value, "png")
        Files.createDirectories(path.parent)
        Files.write(path, bytes)

        runCatching {
            val thumbnail = directory.thumbnails.resolve(id.value.take(2)).resolve("${id.value}.jpg")
            Files.createDirectories(thumbnail.parent)
            Files.write(thumbnail, Thumbnailer.toJpeg(Thumbnailer.thumbnail(image)))
        }

        val document = StoredDocument(
            id = id,
            kind = DocumentKind.RECEIPT,
            format = DocumentFormat.PNG,
            relativePath = directory.relativise(path),
            byteSize = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            importedAt = Instant.now(clock),
            originalFileName = "$merchantName receipt (demo).png",
        )
        store.documents.insert(document)
        return document
    }
}
