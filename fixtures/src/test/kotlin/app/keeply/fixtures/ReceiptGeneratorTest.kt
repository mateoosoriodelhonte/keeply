package app.keeply.fixtures

import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptGeneratorTest {
    @Test
    fun theSameSeedAlwaysProducesTheSameReceipts() {
        // An extraction test that fails has to be reproducible, or it is just noise.
        val first = ReceiptGenerator(seed = 42).corpus(20)
        val second = ReceiptGenerator(seed = 42).corpus(20)
        assertEquals(first, second)
    }

    @Test
    fun differentSeedsProduceDifferentReceipts() {
        val first = ReceiptGenerator(seed = 1).corpus(20)
        val second = ReceiptGenerator(seed = 2).corpus(20)
        assertTrue(first != second)
    }

    @Test
    fun theArithmeticOnTheReceiptAddsUp() {
        ReceiptGenerator(seed = 7).corpus(60).forEach { spec ->
            assertEquals(
                spec.totalMinor,
                spec.subtotalMinor + spec.taxMinor,
                "subtotal plus tax should equal the total for ${spec.merchantName}",
            )
            assertEquals(
                spec.items.sumOf { it.unitPriceMinor * it.quantity },
                if (spec.taxIncludedInPrices) spec.totalMinor else spec.subtotalMinor,
            )
        }
    }

    @Test
    fun handlesTaxInclusivePricingWithoutDoubleCounting() {
        val spec = ReceiptSpec(
            merchantName = "Harbour Grocery Co",
            addressLines = listOf("12 Quay Street"),
            purchaseDate = LocalDate.of(2026, 5, 1),
            time = java.time.LocalTime.NOON,
            items = listOf(ReceiptItem("COFFEE BEANS 500G", 1, 1_200)),
            currency = CurrencyCode.EUR,
            taxRateBasisPoints = 2_000,
            receiptNumber = "R-1",
            paymentLabel = "CASH",
            layout = ReceiptLayout.CLASSIC_TILL,
            taxIncludedInPrices = true,
        )
        assertEquals(Money(1_200, CurrencyCode.EUR), spec.total)
        assertEquals(Money(200, CurrencyCode.EUR), spec.tax)
        assertEquals(Money(1_000, CurrencyCode.EUR), spec.subtotal)
    }

    @Test
    fun coversEveryLayout() {
        val layouts = ReceiptGenerator(seed = 3).corpus(ReceiptLayout.entries.size * 2).map { it.layout }
        assertEquals(ReceiptLayout.entries.toSet(), layouts.toSet())
    }

    @Test
    fun neverUsesARealCompanyName() {
        // Keeply's fixtures are plainly invented documents, not imitations of anyone's
        // real paperwork.
        val names = ReceiptGenerator(seed = 11).corpus(40).map { it.merchantName }.toSet()
        val invented = setOf(
            "Northgate Electronics",
            "Harbour Grocery Co",
            "Pine & Co Hardware",
            "Cedar Home Goods",
            "Fairway Outfitters",
        )
        assertTrue(names.all { it in invented }, "unexpected merchant names: ${names - invented}")
    }
}
