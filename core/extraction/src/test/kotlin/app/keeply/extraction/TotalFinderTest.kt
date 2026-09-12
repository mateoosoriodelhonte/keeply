package app.keeply.extraction

import app.keeply.domain.CurrencyCode
import app.keeply.domain.FieldConfidence
import app.keeply.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotalFinderTest {
    private val finder = TotalFinder()

    private fun dollars(text: String) = Money.of(text, CurrencyCode.USD)

    @Test
    fun trustsATotalTheReceiptAddsUpTo() {
        val text = """
            SUBTOTAL                   $397.85
            TAX                         $39.79
            TOTAL                      $437.64
        """.trimIndent()
        val result = assertNotNull(finder.find(text))
        assertEquals(dollars("437.64"), result.total)
        assertEquals(dollars("397.85"), result.subtotal)
        assertEquals(dollars("39.79"), result.tax)
        assertEquals(FieldConfidence.CONFIDENT, result.confidence)
        assertTrue(result.rule.contains("agrees"))
    }

    @Test
    fun neverReportsTheSubtotalAsTheTotal() {
        // "SUBTOTAL" contains "TOTAL". An extractor that checks for TOTAL first will
        // confidently report the wrong number, and the person will not notice.
        val text = """
            SUBTOTAL                   $100.00
            TAX                          $8.75
            TOTAL                      $108.75
        """.trimIndent()
        assertEquals(dollars("108.75"), assertNotNull(finder.find(text)).total)
    }

    @Test
    fun handlesAbbreviatedTillWords() {
        val text = """
            SUBTL                      $927.02
            TX                          $92.70
            TTL                       $1019.72
        """.trimIndent()
        val result = assertNotNull(finder.find(text))
        assertEquals(dollars("1019.72"), result.total)
        assertEquals(FieldConfidence.CONFIDENT, result.confidence)
    }

    @Test
    fun ignoresCashHandedOverAndChangeGivenBack() {
        // Cash tendered is routinely larger than the total. Taking the largest number
        // would report the wrong one.
        val text = """
            TOTAL                       $18.40
            CASH TENDERED               $20.00
            CHANGE                       $1.60
        """.trimIndent()
        assertEquals(dollars("18.40"), assertNotNull(finder.find(text)).total)
    }

    @Test
    fun understandsATaxInclusiveReceiptWithNoSubtotal() {
        val text = """
            TOTAL                      $450.28
            INCLUDES TAX                $75.05
        """.trimIndent()
        val result = assertNotNull(finder.find(text))
        assertEquals(dollars("450.28"), result.total)
        assertEquals(dollars("75.05"), result.tax)
        assertNull(result.subtotal)
        assertEquals(FieldConfidence.CONFIDENT, result.confidence)
    }

    @Test
    fun readsALabelledTotalWithNothingToCheckItAgainstButSaysSo() {
        val result = assertNotNull(finder.find("AMOUNT DUE                 $205.98"))
        assertEquals(dollars("205.98"), result.total)
        assertEquals(FieldConfidence.UNCERTAIN, result.confidence)
        assertTrue(result.rule.contains("nothing to check"))
    }

    @Test
    fun ignoresTheRateInATaxLabel() {
        // "Sales Tax (7.25%)  $13.92" has two numbers and only one is money.
        val text = """
            Subtotal                   $192.06
            Sales Tax (7.25%)           $13.92
            Amount Due                 $205.98
        """.trimIndent()
        val result = assertNotNull(finder.find(text))
        assertEquals(dollars("205.98"), result.total)
        assertEquals(dollars("13.92"), result.tax)
        assertEquals(FieldConfidence.CONFIDENT, result.confidence)
    }

    @Test
    fun toleratesARoundingCentBetweenTheLines() {
        val text = """
            SUBTOTAL                    $10.00
            TAX                          $0.88
            TOTAL                       $10.89
        """.trimIndent()
        assertEquals(FieldConfidence.CONFIDENT, assertNotNull(finder.find(text)).confidence)
    }

    @Test
    fun fallsBackToTheLargestAmountLowDownAndAdmitsIt() {
        val text = """
            NORTHGATE ELECTRONICS
            HEADPHONES                 $249.99
            CABLE                       $14.99
                                       $264.98
        """.trimIndent()
        val result = assertNotNull(finder.find(text))
        assertEquals(dollars("264.98"), result.total)
        assertEquals(FieldConfidence.UNCERTAIN, result.confidence)
        assertTrue(result.rule.contains("nothing was labelled"))
    }

    @Test
    fun findsNothingInTextWithNoMoneyInIt() {
        assertNull(finder.find("THANK YOU FOR SHOPPING WITH US"))
        assertNull(finder.find(""))
    }
}
