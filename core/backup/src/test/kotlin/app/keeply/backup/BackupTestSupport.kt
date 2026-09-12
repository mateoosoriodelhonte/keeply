package app.keeply.backup

import app.keeply.data.KeeplyDatabaseFactory
import app.keeply.data.KeeplyStore
import app.keeply.documents.DataDirectory
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindowCalculator
import app.keeply.domain.StoredDocument
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import app.keeply.fixtures.ReceiptGenerator
import app.keeply.fixtures.ReceiptImageRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

internal class TestLibrary(root: Path) : AutoCloseable {
    val directory: DataDirectory = DataDirectory(root).create()
    val store: KeeplyStore =
        KeeplyDatabaseFactory.open(directory.database.resolve(KeeplyDatabaseFactory.DATABASE_FILE_NAME))

    private val generator = ReceiptGenerator(seed = 4)

    fun addPurchaseWithReceipt(name: String): Purchase {
        val bytes = ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(generator.spec()))
        val id = DocumentId.new()
        val path = directory.allocate(DocumentKind.RECEIPT, id.value, "png")
        Files.createDirectories(path.parent)
        Files.write(path, bytes)

        val document = StoredDocument(
            id = id,
            kind = DocumentKind.RECEIPT,
            format = DocumentFormat.PNG,
            relativePath = directory.relativise(path),
            byteSize = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            importedAt = Instant.parse("2026-09-12T10:00:00Z"),
            originalFileName = "receipt.png",
        )
        store.documents.insert(document)

        val date = LocalDate.of(2026, 9, 1)
        val purchase = Purchase(
            id = PurchaseId.new(),
            productName = name,
            merchantId = null,
            merchantName = "Northgate Electronics",
            purchaseDate = date,
            price = Money.of("249.99", CurrencyCode.USD),
            tax = Money.of("21.87", CurrencyCode.USD),
            categoryId = null,
            receiptDocumentId = document.id,
            receiptNumber = "TXN-100200",
            returnWindow = ReturnWindowCalculator.resolve(date, ReturnPolicy.Days(30), ReturnPolicySource.USER_ENTERED),
            warranty = Warranty(WarrantyTerm.Months(24), WarrantyProvenance.DOCUMENTED, date, date.plusMonths(24)),
            tags = listOf("audio"),
            createdAt = Instant.parse("2026-09-12T10:00:00Z"),
            updatedAt = Instant.parse("2026-09-12T10:00:00Z"),
        )
        store.purchases.save(purchase, receiptText = "NORTHGATE ELECTRONICS\nTOTAL $249.99")
        return purchase
    }

    override fun close() {
        store.close()
    }
}
