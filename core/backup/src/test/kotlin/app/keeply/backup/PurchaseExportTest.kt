package app.keeply.backup

import app.keeply.domain.Category
import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindowCalculator
import app.keeply.domain.Warranty
import app.keeply.domain.WarrantyProvenance
import app.keeply.domain.WarrantyTerm
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PurchaseExportTest {
    private val today = LocalDate.of(2026, 9, 12)
    private val bought = LocalDate.of(2026, 9, 1)

    private fun purchase(name: String = "Sony Headphones", notes: String? = null, tags: List<String> = listOf("audio")) = Purchase(
        id = PurchaseId("abcdefghijklmnopqrstuvwxyz"),
        productName = name,
        merchantId = null,
        merchantName = "Northgate Electronics",
        purchaseDate = bought,
        price = Money.of("249.99", CurrencyCode.USD),
        tax = Money.of("21.87", CurrencyCode.USD),
        categoryId = Category.defaults().first().id,
        receiptDocumentId = null,
        receiptNumber = "TXN-100200",
        returnWindow = ReturnWindowCalculator.resolve(bought, ReturnPolicy.Days(30), ReturnPolicySource.USER_ENTERED),
        warranty = Warranty(WarrantyTerm.Months(24), WarrantyProvenance.DOCUMENTED, bought, bought.plusMonths(24)),
        notes = notes,
        tags = tags,
        createdAt = Instant.parse("2026-09-12T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-12T10:00:00Z"),
    )

    private fun rows(vararg purchases: Purchase) = PurchaseExport.toRows(purchases.toList(), Category.defaults(), today)

    @Test
    fun writesEveryFieldAPersonWouldWant() {
        val csv = PurchaseExport.toCsv(rows(purchase()))
        val header = csv.lines().first()
        val row = csv.lines()[1]

        assertContains(header, "product")
        assertContains(header, "return_deadline")
        assertContains(header, "warranty_ends")
        assertContains(row, "Sony Headphones")
        assertContains(row, "249.99")
        assertContains(row, "2026-10-01")
        assertContains(row, "Electronics")
    }

    @Test
    fun statusesReadAsWordsRatherThanEnumNames() {
        // The export is for a person with a spreadsheet, not for a machine.
        val csv = PurchaseExport.toCsv(rows(purchase()))
        assertContains(csv, "returnable")
        assertContains(csv, "active")
        assertFalse(csv.contains("ENDS_SOON"))
        assertFalse(csv.contains("USER_ENTERED"))
    }

    @Test
    fun quotesCellsContainingCommasAndQuotes() {
        val csv = PurchaseExport.toCsv(rows(purchase(notes = """He said "buy it", so I did""")))
        assertContains(csv, """"He said ""buy it"", so I did"""")
        assertEquals(2, csv.trim().lines().size, "an embedded comma must not become a new column")
    }

    @Test
    fun keepsANoteWithLineBreaksInOneCell() {
        val csv = PurchaseExport.toCsv(rows(purchase(notes = "First line\nSecond line")))
        assertContains(csv, "\"First line\nSecond line\"")
    }

    @Test
    fun disarmsACellASpreadsheetWouldRunAsAFormula() {
        // A product name beginning with = is executed by every major spreadsheet
        // when the file is opened. This is a real attack against exported data, and
        // the leading apostrophe is invisible once the file is open.
        val csv = PurchaseExport.toCsv(rows(purchase(name = "=cmd|'/c calc'!A1")))
        assertContains(csv, "'=cmd")
        assertFalse(csv.lines()[1].startsWith("=") || csv.contains(",=cmd"))

        listOf("+1+1", "-1+1", "@SUM(A1)").forEach { dangerous ->
            val row = PurchaseExport.toCsv(rows(purchase(name = dangerous))).lines()[1]
            assertContains(row, "'$dangerous")
        }
    }

    @Test
    fun anEmptyFieldIsEmptyRatherThanTheWordNull() {
        val sparse = purchase(notes = null, tags = emptyList()).copy(
            merchantName = null,
            price = null,
            purchaseDate = null,
        )
        val row = PurchaseExport.toCsv(rows(sparse)).lines()[1]
        assertFalse(row.contains("null"), "row was: $row")
        val cells = row.split(",")
        assertEquals("", cells[2], "an unknown merchant is an empty cell")
        assertEquals("", cells[3], "an unknown date is an empty cell")
    }

    @Test
    fun saysUnknownWhereThereIsNothingToSay() {
        val blank = Purchase(
            id = PurchaseId("abcdefghijklmnopqrstuvwxyz"),
            productName = "Something",
            merchantId = null,
            merchantName = null,
            purchaseDate = null,
            price = null,
            categoryId = null,
            receiptDocumentId = null,
            createdAt = Instant.parse("2026-09-12T10:00:00Z"),
            updatedAt = Instant.parse("2026-09-12T10:00:00Z"),
        )
        val row = PurchaseExport.toCsv(rows(blank)).lines()[1]
        assertContains(row, "unknown")
    }

    @Test
    fun jsonCarriesTheSameFacts() {
        val json = PurchaseExport.toJson(rows(purchase()), "1.0.0")
        assertContains(json, "\"product\": \"Sony Headphones\"")
        assertContains(json, "\"return_deadline\": \"2026-10-01\"")
        assertContains(json, "\"warranty_status\": \"active\"")
        assertContains(json, "\"app_version\": \"1.0.0\"")
        assertContains(json, "\"tags\": [")
    }

    @Test
    fun exportsNothingAsAHeaderRatherThanAnEmptyFile() {
        val csv = PurchaseExport.toCsv(emptyList())
        assertEquals(1, csv.trim().lines().size)
        assertTrue(csv.startsWith("id,product,merchant"))
    }
}
