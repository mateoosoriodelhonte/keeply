package app.keeply.extraction

import app.keeply.domain.FieldConfidence
import app.keeply.domain.Merchant
import app.keeply.domain.MerchantId
import app.keeply.domain.ReturnPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MerchantFinderTest {
    private val finder = MerchantFinder()

    private val header = """
        NORTHGATE ELECTRONICS
        814 Mariner Way
        Bellhaven, WA 98004
        (555) 0142

        09/01/2026  11:04
        HEADPHONES                 $249.99
    """.trimIndent()

    @Test
    fun takesTheShopNameFromTheTopOfTheReceipt() {
        val found = assertNotNull(finder.find(header))
        assertEquals("Northgate Electronics", found.name)
        assertEquals(FieldConfidence.UNCERTAIN, found.confidence)
    }

    @Test
    fun skipsTheAddressAndPhoneNumberUnderTheName() {
        val addressFirst = """
            814 Mariner Way
            NORTHGATE ELECTRONICS
            (555) 0142
        """.trimIndent()
        assertEquals("Northgate Electronics", assertNotNull(finder.find(addressFirst)).name)
    }

    @Test
    fun recognisesAShopThePersonHasSavedAndBringsTheirRuleWithIt() {
        val saved = Merchant(
            id = MerchantId.new(),
            name = "Northgate Electronics",
            defaultReturnPolicy = ReturnPolicy.Days(15),
        )
        val found = assertNotNull(finder.find(header, listOf(saved)))
        assertEquals(FieldConfidence.CONFIDENT, found.confidence)
        assertEquals(saved.id, found.matchedMerchant?.id)
        assertEquals(ReturnPolicy.Days(15), found.matchedMerchant?.defaultReturnPolicy)
    }

    @Test
    fun aSavedNameBuriedInATillHeaderIsOfferedButNotAsserted() {
        val saved = Merchant(id = MerchantId.new(), name = "Harbour Grocery")
        val text = "HARBOUR GROCERY CO #1184 PORT ALDEN\n12 Quay Street"
        val found = assertNotNull(finder.find(text, listOf(saved)))
        assertEquals(FieldConfidence.UNCERTAIN, found.confidence)
        assertEquals(saved.id, found.matchedMerchant?.id)
    }

    @Test
    fun tidiesAShoutingTillHeader() {
        assertEquals("Cedar Home Goods", assertNotNull(finder.find("CEDAR HOME GOODS\n59 Lantern Street")).name)
    }

    @Test
    fun leavesAProperlyCasedNameAlone() {
        assertEquals("Pine & Co Hardware", assertNotNull(finder.find("Pine & Co Hardware\n2210 Sawmill Road")).name)
    }

    @Test
    fun findsNothingWhenTheTopOfTheReceiptIsNotAName() {
        assertNull(finder.find("========================\n12/25/2026\n$10.00"))
        assertNull(finder.find(""))
    }
}
