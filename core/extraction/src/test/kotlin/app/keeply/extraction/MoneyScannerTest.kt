package app.keeply.extraction

import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyScannerTest {
    private val scanner = MoneyScanner(CurrencyCode.USD)

    private fun amounts(line: String) = scanner.scanLine(line).map { it.money }

    @Test
    fun findsAmountsOnATillLine() {
        assertEquals(listOf(Money(13_734, CurrencyCode.USD)), amounts("TRAIL RUNNING SHOES        $137.34"))
        assertEquals(listOf(Money(43_764, CurrencyCode.USD)), amounts("TOTAL                      $437.64"))
    }

    @Test
    fun findsBothAmountsOnAQuantityLine() {
        assertEquals(
            listOf(Money(3_173, CurrencyCode.USD), Money(12_692, CurrencyCode.USD)),
            amounts("  4 @ $31.73                 $126.92"),
        )
    }

    @Test
    fun readsGroupedThousands() {
        assertEquals(listOf(Money(101_972, CurrencyCode.USD)), amounts("TOTAL   $1,019.72"))
    }

    @Test
    fun ignoresTheDate() {
        // A receipt is full of digits that are not money, and this is the one that
        // would otherwise be read as $5.09 or $2,026.
        assertEquals(emptyList(), amounts("05/09/2026  17:19"))
        assertEquals(emptyList(), amounts("Date: 2026-06-17    Time: 19:17"))
        assertEquals(emptyList(), amounts("10.10.26 20:29"))
    }

    @Test
    fun ignoresACardSuffix() {
        assertEquals(emptyList(), amounts("DEBIT ****4412"))
        assertEquals(emptyList(), amounts("VISA ****1234"))
    }

    @Test
    fun ignoresBareQuantitiesAndYears() {
        assertEquals(emptyList(), amounts("QTY  ITEM"))
        assertEquals(emptyList(), amounts("Aspen Hollow, CO 81611"))
        assertEquals(emptyList(), amounts("4"))
    }

    @Test
    fun readsANonDollarCurrencyFromItsSymbol() {
        assertEquals(listOf(Money(1_250, CurrencyCode.EUR)), amounts("Total   €12,50"))
    }

    @Test
    fun repairsCharactersOcrConfusedForDigits() {
        // Tesseract reads a zero as an O and a one as an l often enough that ignoring
        // it would lose real totals.
        val matches = scanner.scanLine("TOTAL   \$l2.5O")
        assertEquals(listOf(Money(1_250, CurrencyCode.USD)), matches.map { it.money })
        assertTrue(matches.single().repaired, "the match should say it needed repair")
    }

    @Test
    fun doesNotRepairWordsThatHappenToContainThoseLetters() {
        // A shop called SOHO must not become 5OHO.
        assertEquals(emptyList(), amounts("SOHO BOOKS"))
        assertEquals(emptyList(), amounts("OSLO"))
    }

    @Test
    fun recordsWhereOnTheLineItFoundTheAmount() {
        // Column position is how the total is told apart from the item beside it.
        val match = scanner.scanLine("SUBTOTAL                   $397.85").single()
        assertTrue(match.startColumn > 20, "the amount is right-aligned, was at ${match.startColumn}")
        assertEquals("$397.85", match.raw)
    }

    @Test
    fun readsNegativeAmountsForRefundsAndDiscounts() {
        assertEquals(listOf(Money(-500, CurrencyCode.USD)), amounts("DISCOUNT   -$5.00"))
        assertEquals(listOf(Money(-500, CurrencyCode.USD)), amounts("COUPON   ($5.00)"))
    }
}
