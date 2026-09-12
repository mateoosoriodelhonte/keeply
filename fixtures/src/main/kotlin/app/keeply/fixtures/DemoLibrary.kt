package app.keeply.fixtures

import app.keeply.domain.CurrencyCode
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.WarrantyTerm
import java.time.LocalDate
import java.time.LocalTime

/**
 * One demo purchase: the receipt to generate, and what the purchase made from it
 * should look like.
 */
public data class DemoPurchase(
    val productName: String,
    val categoryId: String,
    val receipt: ReceiptSpec,
    val returnPolicy: ReturnPolicy,
    val warranty: WarrantyTerm,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val serialNumber: String? = null,
)

/**
 * A small, believable library for someone trying Keeply out.
 *
 * Dates are relative to today, so the demo always shows a return window about to
 * close and a warranty about to expire rather than a screen of expired entries.
 * Everything here is invented and is marked as demo data in the database, so
 * clearing it can never touch anything real.
 */
public object DemoLibrary {

    public fun purchases(today: LocalDate = LocalDate.now()): List<DemoPurchase> = listOf(
        // A return window closing tomorrow: the thing the home screen exists for.
        demo(
            productName = "Trail Running Shoes",
            category = "clothing",
            shop = "Fairway Outfitters",
            address = listOf("77 Ridgeline Avenue", "Aspen Hollow, CO 81611"),
            purchasedDaysAgo = 44,
            today = today,
            items = listOf(ReceiptItem("TRAIL RUNNING SHOES", 1, 13_500), ReceiptItem("MERINO SOCKS 3PK", 1, 3_450)),
            layout = ReceiptLayout.CLASSIC_TILL,
            returnPolicy = ReturnPolicy.Days(45),
            warranty = WarrantyTerm.Unknown,
            returnLine = "Returns within 45 days, worn items excluded",
            tags = listOf("running"),
        ),
        // A warranty expiring in under three weeks.
        demo(
            productName = "Espresso Machine",
            category = "appliances",
            shop = "Cedar Home Goods",
            address = listOf("59 Lantern Street", "Marlow Bay, CA 94019"),
            purchasedDaysAgo = 347,
            today = today,
            items = listOf(ReceiptItem("ESPRESSO MACHINE 1450W", 1, 34_900)),
            layout = ReceiptLayout.INVOICE,
            returnPolicy = ReturnPolicy.Days(60),
            warranty = WarrantyTerm.Months(12),
            notes = "Descale every two months. Filter size 58mm.",
            serialNumber = "CHG-4471-88",
        ),
        demo(
            productName = "Wireless Headphones",
            category = "electronics",
            shop = "Northgate Electronics",
            address = listOf("814 Mariner Way", "Bellhaven, WA 98004"),
            purchasedDaysAgo = 9,
            today = today,
            items = listOf(ReceiptItem("WIRELESS HEADPHONES", 1, 24_999), ReceiptItem("USB-C CABLE 2M", 1, 1_499)),
            layout = ReceiptLayout.COLUMNAR,
            returnPolicy = ReturnPolicy.Days(30),
            warranty = WarrantyTerm.Months(24),
            tags = listOf("audio", "travel"),
            serialNumber = "NE-77219043",
        ),
        demo(
            productName = "Office Chair",
            category = "furniture",
            shop = "Cedar Home Goods",
            address = listOf("59 Lantern Street", "Marlow Bay, CA 94019"),
            purchasedDaysAgo = 21,
            today = today,
            items = listOf(ReceiptItem("OFFICE CHAIR", 1, 17_900)),
            layout = ReceiptLayout.INVOICE,
            returnPolicy = ReturnPolicy.Days(60),
            warranty = WarrantyTerm.Months(60),
            notes = "Assembly hex key in the seat pocket.",
        ),
        demo(
            productName = "Blender",
            category = "appliances",
            shop = "Cedar Home Goods",
            address = listOf("59 Lantern Street", "Marlow Bay, CA 94019"),
            purchasedDaysAgo = 33,
            today = today,
            items = listOf(ReceiptItem("BLENDER 1200W", 1, 8_995)),
            layout = ReceiptLayout.CLASSIC_TILL,
            returnPolicy = ReturnPolicy.Days(60),
            warranty = WarrantyTerm.Months(24),
        ),
        demo(
            productName = "Cordless Drill",
            category = "automotive",
            shop = "Pine & Co Hardware",
            address = listOf("2210 Sawmill Road", "Fernwood, OR 97051"),
            purchasedDaysAgo = 120,
            today = today,
            items = listOf(ReceiptItem("CORDLESS DRILL 18V", 1, 13_999), ReceiptItem("WOOD SCREWS 100PK", 2, 1_299)),
            layout = ReceiptLayout.COMPACT,
            returnPolicy = ReturnPolicy.Days(90),
            warranty = WarrantyTerm.Lifetime,
            notes = "Lifetime warranty on the chuck, battery is three years.",
        ),
        demo(
            productName = "27-inch Monitor",
            category = "electronics",
            shop = "Northgate Electronics",
            address = listOf("814 Mariner Way", "Bellhaven, WA 98004"),
            purchasedDaysAgo = 210,
            today = today,
            items = listOf(ReceiptItem("27IN MONITOR", 1, 31_900), ReceiptItem("HDMI ADAPTER", 1, 2_299)),
            layout = ReceiptLayout.COLUMNAR,
            returnPolicy = ReturnPolicy.Days(30),
            warranty = WarrantyTerm.Months(36),
            serialNumber = "NE-90881277",
        ),
        demo(
            productName = "Table Lamp",
            category = "home",
            shop = "Cedar Home Goods",
            address = listOf("59 Lantern Street", "Marlow Bay, CA 94019"),
            purchasedDaysAgo = 3,
            today = today,
            items = listOf(ReceiptItem("TABLE LAMP", 1, 5_400)),
            layout = ReceiptLayout.CLASSIC_TILL,
            returnPolicy = ReturnPolicy.Days(60),
            warranty = WarrantyTerm.Months(12),
        ),
    )

    @Suppress("LongParameterList")
    private fun demo(
        productName: String,
        category: String,
        shop: String,
        address: List<String>,
        purchasedDaysAgo: Long,
        today: LocalDate,
        items: List<ReceiptItem>,
        layout: ReceiptLayout,
        returnPolicy: ReturnPolicy,
        warranty: WarrantyTerm,
        returnLine: String? = null,
        notes: String? = null,
        tags: List<String> = emptyList(),
        serialNumber: String? = null,
    ): DemoPurchase {
        val date = today.minusDays(purchasedDaysAgo)
        return DemoPurchase(
            productName = productName,
            categoryId = category,
            receipt = ReceiptSpec(
                merchantName = shop,
                addressLines = address,
                purchaseDate = date,
                time = LocalTime.of(14, 7),
                items = items,
                currency = CurrencyCode.USD,
                taxRateBasisPoints = 875,
                receiptNumber = "TXN-${100_000 + productName.hashCode().mod(899_999)}",
                paymentLabel = "VISA ****1234",
                layout = layout,
                returnPolicyLine = returnLine,
            ),
            returnPolicy = returnPolicy,
            warranty = warranty,
            notes = notes,
            tags = tags,
            serialNumber = serialNumber,
        )
    }
}
