package app.keeply.fixtures

import app.keeply.domain.CurrencyCode
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Makes receipts up.
 *
 * Deterministic given a seed, so an extraction test that fails can be reproduced
 * exactly. Every shop and product here is invented; Keeply's test data never
 * imitates a real company's paperwork and never contains anybody's real receipt.
 */
public class ReceiptGenerator(seed: Long = DEFAULT_SEED) {
    private val random = Random(seed)

    public fun spec(
        layout: ReceiptLayout = ReceiptLayout.entries.random(random),
        currency: CurrencyCode = CurrencyCode.USD,
        date: LocalDate? = null,
        itemCount: Int = random.nextInt(1, 8),
    ): ReceiptSpec {
        val shop = SHOPS.random(random)
        // Distinct products, the way a real basket looks. The same product listed
        // twice at two different prices would be a generator artefact, not a receipt.
        val chosen = shop.catalogue.shuffled(random).take(itemCount.coerceAtMost(shop.catalogue.size))
        val items = chosen.map(::randomItem)
        return ReceiptSpec(
            merchantName = shop.name,
            addressLines = shop.address,
            purchaseDate = date ?: randomDate(),
            time = LocalTime.of(random.nextInt(8, 21), random.nextInt(0, 60)),
            items = items,
            currency = currency,
            taxRateBasisPoints = TAX_RATES.random(random),
            receiptNumber = randomReceiptNumber(),
            paymentLabel = PAYMENT_LABELS.random(random),
            layout = layout,
            returnPolicyLine = shop.returnLine.takeIf { random.nextInt(100) < RETURN_LINE_PERCENT },
            taxIncludedInPrices = random.nextInt(100) < TAX_INCLUDED_PERCENT,
            groupsThousands = random.nextInt(100) < THOUSANDS_PERCENT,
        )
    }

    /** A spread of layouts, currencies and sizes for testing extraction broadly. */
    public fun corpus(count: Int): List<ReceiptSpec> = List(count) { index ->
        spec(
            layout = ReceiptLayout.entries[index % ReceiptLayout.entries.size],
            currency = if (index % CURRENCY_EVERY == 0) CurrencyCode.EUR else CurrencyCode.USD,
        )
    }

    private fun randomItem(entry: Pair<String, Long>): ReceiptItem {
        val (name, base) = entry
        val jitter = random.nextLong(-base / 5, base / 5 + 1)
        return ReceiptItem(
            description = name,
            quantity = if (random.nextInt(100) < MULTI_QUANTITY_PERCENT) random.nextInt(2, 5) else 1,
            unitPriceMinor = (base + jitter).coerceAtLeast(99L),
        )
    }

    private fun randomDate(): LocalDate = LocalDate.of(2026, 1, 1).plusDays(random.nextLong(0, 300))

    private fun randomReceiptNumber(): String = when (random.nextInt(4)) {
        0 -> "TXN-${random.nextInt(100_000, 999_999)}"
        1 -> "#${random.nextInt(1000, 9999)}-${random.nextInt(10, 99)}"
        2 -> "ORDER ${random.nextInt(10_000_000, 99_999_999)}"
        else -> "R${random.nextInt(100_000, 999_999)}"
    }

    private data class Shop(val name: String, val address: List<String>, val returnLine: String, val catalogue: List<Pair<String, Long>>)

    private companion object {
        const val DEFAULT_SEED = 20_260_912L
        const val RETURN_LINE_PERCENT = 45
        const val TAX_INCLUDED_PERCENT = 20
        const val MULTI_QUANTITY_PERCENT = 25
        const val THOUSANDS_PERCENT = 50
        const val CURRENCY_EVERY = 5

        val TAX_RATES = listOf(0, 500, 625, 725, 800, 875, 1000, 2000)

        val PAYMENT_LABELS = listOf(
            "VISA ****1234",
            "MASTERCARD ****9087",
            "DEBIT ****4412",
            "CASH",
            "GIFT CARD",
        )

        // Every shop and product below is invented for testing.
        val SHOPS = listOf(
            Shop(
                name = "Northgate Electronics",
                address = listOf("814 Mariner Way", "Bellhaven, WA 98004", "(555) 0142"),
                returnLine = "Returns accepted within 30 days with receipt",
                catalogue = listOf(
                    "WIRELESS HEADPHONES" to 24_999L,
                    "USB-C CABLE 2M" to 1_499L,
                    "27IN MONITOR" to 31_900L,
                    "MECH KEYBOARD" to 12_950L,
                    "PORTABLE SSD 1TB" to 10_400L,
                    "HDMI ADAPTER" to 2_299L,
                ),
            ),
            Shop(
                name = "Harbour Grocery Co",
                address = listOf("12 Quay Street", "Port Alden, ME 04011"),
                returnLine = "Perishables are final sale",
                catalogue = listOf(
                    "OAT MILK 1L" to 449L,
                    "SOURDOUGH LOAF" to 625L,
                    "COFFEE BEANS 500G" to 1_395L,
                    "OLIVE OIL 750ML" to 1_899L,
                    "FREE RANGE EGGS" to 699L,
                ),
            ),
            Shop(
                name = "Pine & Co Hardware",
                address = listOf("2210 Sawmill Road", "Fernwood, OR 97051"),
                returnLine = "Return within 90 days for exchange or refund",
                catalogue = listOf(
                    "CORDLESS DRILL 18V" to 13_999L,
                    "PAINT ROLLER SET" to 1_850L,
                    "TIMBER 2X4 8FT" to 949L,
                    "WOOD SCREWS 100PK" to 1_299L,
                    "STEP LADDER 6FT" to 8_450L,
                ),
            ),
            Shop(
                name = "Cedar Home Goods",
                address = listOf("59 Lantern Street", "Marlow Bay, CA 94019"),
                returnLine = "Unused items may be returned within 60 days",
                catalogue = listOf(
                    "OFFICE CHAIR" to 17_900L,
                    "TABLE LAMP" to 5_400L,
                    "WOOL THROW" to 7_250L,
                    "CERAMIC MUG SET" to 3_199L,
                    "BLENDER 1200W" to 8_995L,
                ),
            ),
            Shop(
                name = "Fairway Outfitters",
                address = listOf("77 Ridgeline Avenue", "Aspen Hollow, CO 81611"),
                returnLine = "Returns within 45 days, worn items excluded",
                catalogue = listOf(
                    "TRAIL RUNNING SHOES" to 13_500L,
                    "RAIN JACKET" to 18_900L,
                    "MERINO SOCKS 3PK" to 3_450L,
                    "HYDRATION PACK" to 9_900L,
                    "HEADLAMP 400LM" to 4_299L,
                ),
            ),
        )
    }
}
