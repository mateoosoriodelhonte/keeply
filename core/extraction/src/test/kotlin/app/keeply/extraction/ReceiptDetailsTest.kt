package app.keeply.extraction

import app.keeply.domain.FieldConfidence
import app.keeply.domain.ReturnPolicy
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReceiptDetailsTest {
    private val numbers = ReceiptNumberFinder()
    private val policies = ReturnPolicyFinder { LocalDate.of(2026, 9, 12) }

    @Test
    fun findsALabelledReceiptNumber() {
        listOf(
            "Receipt TXN-214034" to "TXN-214034",
            "Transaction: ORDER 44259208" to "ORDER 44259208",
            "Invoice number : R671247" to "R671247",
            "Order #10029384" to "10029384",
        ).forEach { (line, expected) ->
            val found = assertNotNull(numbers.find(line), "nothing found in '$line'")
            assertEquals(expected, found.number)
            assertEquals(FieldConfidence.CONFIDENT, found.confidence)
        }
    }

    @Test
    fun offersAnUnlabelledCodeButDoesNotTrustIt() {
        // Tills print register numbers and cashier ids that look just like this.
        val found = assertNotNull(numbers.find("NORTHGATE ELECTRONICS\nTXN-998123\n"))
        assertEquals("TXN-998123", found.number)
        assertEquals(FieldConfidence.UNCERTAIN, found.confidence)
    }

    @Test
    fun refusesNumbersThatCouldBeAnything() {
        assertNull(numbers.find("Store 1234"), "a bare four-digit number is as likely to be a year")
        assertNull(numbers.find("THANK YOU"))
    }

    @Test
    fun readsAReturnWindowPrintedOnTheReceipt() {
        assertEquals(
            ReturnPolicy.Days(45),
            assertNotNull(policies.find("Returns within 45 days, worn items excluded")).policy,
        )
        assertEquals(
            ReturnPolicy.Days(30),
            assertNotNull(policies.find("Returns accepted within 30 days with receipt")).policy,
        )
        assertEquals(
            ReturnPolicy.Days(90),
            assertNotNull(policies.find("Return within 90 days for exchange or refund")).policy,
        )
    }

    @Test
    fun readsAWindowPrintedInMonths() {
        assertEquals(ReturnPolicy.Days(180), assertNotNull(policies.find("Exchanges accepted for 6 months")).policy)
    }

    @Test
    fun readsAPrintedReturnByDate() {
        val found = assertNotNull(policies.find("Return by 10/01/2026 with this receipt"))
        assertEquals(ReturnPolicy.Until(LocalDate.of(2026, 10, 1)), found.policy)
    }

    @Test
    fun keepsTheWordsItReadThePolicyFrom() {
        // What lets the interface say where a deadline came from instead of asserting it.
        val found = assertNotNull(policies.find("Returns within 45 days, worn items excluded"))
        assertEquals("Returns within 45 days, worn items excluded", found.evidence)
    }

    @Test
    fun findsNothingInAVaguePolicyLine() {
        // Keeply must not turn "see our policy online" into a deadline.
        assertNull(policies.find("See our returns policy online at example.invalid"))
        assertNull(policies.find("Thank you for shopping with us"))
        assertNull(policies.find("Perishables are final sale"))
    }

    @Test
    fun doesNotMistakeAnUnrelatedNumberForAReturnWindow() {
        assertNull(policies.find("Open 7 days a week"), "opening hours are not a return policy")
    }
}
