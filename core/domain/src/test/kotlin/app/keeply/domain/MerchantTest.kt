package app.keeply.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MerchantTest {
    @Test
    fun normalisesTillSpellingsToOneKey() {
        assertEquals("best buy", Merchant.normaliseName("BEST BUY #1234"))
        assertEquals("best buy", Merchant.normaliseName("Best Buy Co., Inc."))
        assertEquals("target", Merchant.normaliseName("TARGET  STORES"))
        assertEquals("acme hardware", Merchant.normaliseName("ACME Hardware LLC"))
        assertEquals("7 eleven", Merchant.normaliseName("7-ELEVEN"), "a leading number is part of the name")
    }

    @Test
    fun matchesOnAliasesSeenOnReceipts() {
        val merchant = Merchant(
            id = MerchantId.new(),
            name = "Best Buy",
            aliases = listOf("BESTBUY.COM", "BBY"),
        )
        assertTrue("best buy" in merchant.matchKeys)
        assertTrue("bestbuy com" in merchant.matchKeys)
        assertTrue("bby" in merchant.matchKeys)
    }

    @Test
    fun labelsASavedRuleAsTheUsersOwn() {
        // Keeply has no way to know a retailer's real policy, and saying otherwise
        // could cost someone a refund.
        val merchant = Merchant(
            id = MerchantId.new(),
            name = "Best Buy",
            defaultReturnPolicy = ReturnPolicy.Days(15),
        )
        assertEquals("Your saved rule: 15 days", merchant.returnRuleLabel())
        assertTrue("policy" !in merchant.returnRuleLabel().lowercase())
    }

    @Test
    fun saysSoWhenThereIsNoSavedRule() {
        val merchant = Merchant(id = MerchantId.new(), name = "Corner Shop")
        assertEquals("No saved rule", merchant.returnRuleLabel())
    }

    @Test
    fun requiresAName() {
        assertFailsWith<IllegalArgumentException> { Merchant(id = MerchantId.new(), name = "  ") }
    }
}
