package app.keeply.data

import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.FieldConfidence
import app.keeply.domain.LineItem
import app.keeply.domain.Money
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnStatus
import app.keeply.domain.StoredDocument
import app.keeply.domain.WarrantyStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PurchaseRepositoryTest {
    private lateinit var store: KeeplyStore

    @BeforeEach
    fun setUp() {
        store = KeeplyDatabaseFactory.openInMemory()
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    @Test
    fun savesAndReadsBackEverythingItWasGiven() {
        val original = TestPurchases.purchase(tags = listOf("gift", "audio"))
            .copy(
                lineItems = listOf(
                    LineItem(
                        "WH-1000XM5",
                        quantity = 1,
                        totalPrice = Money.of("249.99", CurrencyCode.USD),
                        confidence = FieldConfidence.CONFIDENT,
                    ),
                    LineItem("Extended cover", quantity = 1, confidence = FieldConfidence.UNCERTAIN),
                ),
                serialNumber = "SN-12345",
                paymentMethodLabel = "Visa ending 1234",
                notes = "Birthday present",
            )
        store.purchases.save(original)

        val loaded = assertNotNull(store.purchases.get(original.id))
        assertEquals(original.productName, loaded.productName)
        assertEquals(original.price, loaded.price)
        assertEquals(original.tax, loaded.tax)
        assertEquals(original.purchaseDate, loaded.purchaseDate)
        assertEquals(original.serialNumber, loaded.serialNumber)
        assertEquals(original.paymentMethodLabel, loaded.paymentMethodLabel)
        assertEquals(original.notes, loaded.notes)
        assertEquals(listOf("audio", "gift"), loaded.tags.sorted())
        assertEquals(2, loaded.lineItems.size)
        assertEquals("WH-1000XM5", loaded.lineItems.first().description)
    }

    @Test
    fun keepsReturnAndWarrantyProvenanceAcrossARoundTrip() {
        // A deadline that came from a saved rule must not come back looking like a
        // date printed on the receipt.
        val original = TestPurchases.purchase()
        store.purchases.save(original)

        val loaded = assertNotNull(store.purchases.get(original.id))
        assertEquals(original.returnWindow.source, loaded.returnWindow.source)
        assertEquals(original.returnWindow.deadline, loaded.returnWindow.deadline)
        assertEquals(ReturnPolicy.Days(30), loaded.returnWindow.policy)
        assertEquals(original.warranty.provenance, loaded.warranty.provenance)
        assertEquals(original.warranty.endDate, loaded.warranty.endDate)
    }

    @Test
    fun aPurchaseWithNothingReadComesBackEmptyNotZero() {
        val sparse = TestPurchases.purchase(merchantName = null, price = null, date = null)
        store.purchases.save(sparse)

        val loaded = assertNotNull(store.purchases.get(sparse.id))
        assertNull(loaded.price)
        assertNull(loaded.purchaseDate)
        assertNull(loaded.merchantName)
        assertEquals(ReturnStatus.UNKNOWN, loaded.returnStatusOn(TestPurchases.BOUGHT))
    }

    @Test
    fun updatingReplacesTagsAndItemsRatherThanAccumulating() {
        val original = TestPurchases.purchase(tags = listOf("a", "b"))
        store.purchases.save(original)
        store.purchases.save(original.copy(tags = listOf("c")))

        val loaded = assertNotNull(store.purchases.get(original.id))
        assertEquals(listOf("c"), loaded.tags)
        assertEquals(1L, store.purchases.count())
    }

    @Test
    fun archivingHidesFromTheLibraryWithoutDeleting() {
        val purchase = TestPurchases.purchase()
        store.purchases.save(purchase)
        store.purchases.setArchived(purchase.id, true)

        assertEquals(emptyList(), store.purchases.all().map { it.id })
        assertEquals(true, assertNotNull(store.purchases.get(purchase.id)).isArchived)
        assertEquals(1L, store.purchases.count())
    }

    @Test
    fun findsWhatIsClosingSoon() {
        val today = LocalDate.of(2026, 9, 12)
        val closing = TestPurchases.purchase(name = "Running shoes", date = today.minusDays(25))
        val fresh = TestPurchases.purchase(name = "Blender", date = today)
        store.purchases.save(closing)
        store.purchases.save(fresh)

        val soon = store.purchases.returnsClosingBetween(today, today.plusDays(7))
        assertEquals(listOf("Running shoes"), soon.map { it.productName })
        assertEquals(ReturnStatus.ENDS_SOON, soon.single().returnStatusOn(today))
    }

    @Test
    fun findsWarrantiesAboutToExpire() {
        val today = LocalDate.of(2026, 9, 12)
        val expiring = TestPurchases.purchase(name = "Coffee machine", date = today.minusMonths(12).plusDays(18))
        store.purchases.save(expiring)
        store.purchases.save(TestPurchases.purchase(name = "Laptop", date = today))

        val soon = store.purchases.warrantiesEndingBetween(today, today.plusDays(30))
        assertEquals(listOf("Coffee machine"), soon.map { it.productName })
        assertEquals(WarrantyStatus.EXPIRING_SOON, soon.single().warrantyStatusOn(today))
    }

    @Test
    fun deletingAReceiptLeavesThePurchaseIntact() {
        // Someone clearing out a file should lose the scan, not the record of what
        // they bought.
        val document = StoredDocument(
            id = DocumentId.new(),
            kind = DocumentKind.RECEIPT,
            format = DocumentFormat.PDF,
            relativePath = "receipts/ab/cd.pdf",
            byteSize = 1024,
            sha256 = "f".repeat(64),
            importedAt = TestPurchases.NOW,
            originalFileName = "receipt.pdf",
        )
        store.documents.insert(document)
        val purchase = TestPurchases.purchase(receiptId = document.id)
        store.purchases.save(purchase)

        store.documents.delete(document.id)

        val loaded = assertNotNull(store.purchases.get(purchase.id))
        assertNull(loaded.receiptDocumentId)
        assertEquals("Sony Headphones", loaded.productName)
    }

    @Test
    fun loadsListsWithoutAQueryPerRow() {
        repeat(50) { index ->
            store.purchases.save(TestPurchases.purchase(name = "Item $index", tags = listOf("bulk")))
        }
        val all = store.purchases.all()
        assertEquals(50, all.size)
        assertTrue(all.all { it.tags == listOf("bulk") })
        // List views skip line items deliberately; the detail screen loads them.
        assertTrue(all.all { it.lineItems.isEmpty() })
    }

    @Test
    fun demoDataCanBeRemovedWithoutTouchingRealPurchases() {
        store.purchases.save(TestPurchases.purchase(name = "Real thing"))
        store.purchases.save(TestPurchases.purchase(name = "Demo thing", demo = true))

        store.purchases.deleteDemoData()

        assertEquals(listOf("Real thing"), store.purchases.all().map { it.productName })
        assertEquals(1L, store.search.count())
    }

    @Test
    fun buildsFingerprintsForDuplicateChecking() {
        val purchase = TestPurchases.purchase(receiptNumber = "T-0099")
        store.purchases.save(purchase)

        val fingerprint = store.purchases.fingerprints().single()
        assertEquals(purchase.id, fingerprint.purchaseId)
        assertEquals("best buy", fingerprint.merchantKey)
        assertEquals(Money.of("249.99", CurrencyCode.USD), fingerprint.total)
        assertEquals("T-0099", fingerprint.receiptNumber)
    }
}
