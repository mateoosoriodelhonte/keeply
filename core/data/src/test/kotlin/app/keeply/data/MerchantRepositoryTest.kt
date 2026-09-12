package app.keeply.data

import app.keeply.domain.FieldConfidence
import app.keeply.domain.Merchant
import app.keeply.domain.MerchantId
import app.keeply.domain.ReturnPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MerchantRepositoryTest {
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
    fun savesAndReadsBackASavedReturnRule() {
        val merchant = Merchant(
            id = MerchantId.new(),
            name = "Best Buy",
            aliases = listOf("BESTBUY.COM"),
            defaultReturnPolicy = ReturnPolicy.Days(15),
            notes = "Membership extends this",
        )
        store.merchants.save(merchant)

        val loaded = assertNotNull(store.merchants.get(merchant.id))
        assertEquals("Best Buy", loaded.name)
        assertEquals(ReturnPolicy.Days(15), loaded.defaultReturnPolicy)
        assertEquals(listOf("BESTBUY.COM"), loaded.aliases)
        assertEquals("Membership extends this", loaded.notes)
    }

    @Test
    fun findsAShopByHoweverItWasTyped() {
        store.merchants.save(Merchant(id = MerchantId.new(), name = "Best Buy"))
        assertNotNull(store.merchants.findByName("BEST BUY"))
        assertNotNull(store.merchants.findByName("Best Buy Co., Inc."))
        assertNull(store.merchants.findByName("Target"))
    }

    @Test
    fun recognisesAShopFromTheTopOfAReceipt() {
        store.merchants.save(Merchant(id = MerchantId.new(), name = "Best Buy", defaultReturnPolicy = ReturnPolicy.Days(15)))
        val match = assertNotNull(
            store.merchants.match("BEST BUY\n1234 Market Street\n\nWH-1000XM5\nTOTAL 249.99"),
        )
        assertEquals("Best Buy", match.merchant.name)
        assertEquals(FieldConfidence.CONFIDENT, match.confidence)
    }

    @Test
    fun treatsAShopNameBuriedInALongerLineAsUncertain() {
        // Till headers carry addresses and branch numbers, so this is a guess worth
        // showing for review rather than applying quietly.
        store.merchants.save(Merchant(id = MerchantId.new(), name = "Costco"))
        val match = assertNotNull(store.merchants.match("COSTCO WHOLESALE CORPORATION SEATTLE WA"))
        assertEquals(FieldConfidence.UNCERTAIN, match.confidence)
    }

    @Test
    fun doesNotGuessWhenNothingMatches() {
        store.merchants.save(Merchant(id = MerchantId.new(), name = "Costco"))
        assertNull(store.merchants.match("SOME OTHER SHOP\nTOTAL 10.00"))
        assertNull(store.merchants.match(""))
    }

    @Test
    fun looksOnlyNearTheTopOfTheReceipt() {
        store.merchants.save(Merchant(id = MerchantId.new(), name = "Target"))
        val deepInItemList = buildString {
            repeat(20) { appendLine("ITEM $it") }
            appendLine("TARGET")
        }
        assertNull(store.merchants.match(deepInItemList))
    }
}
