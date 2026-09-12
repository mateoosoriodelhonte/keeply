package app.keeply.data

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchRepositoryTest {
    private lateinit var store: KeeplyStore

    @BeforeEach
    fun setUp() {
        store = KeeplyDatabaseFactory.openInMemory()
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    private fun seed() {
        store.purchases.save(
            TestPurchases.purchase(name = "Sony Headphones", merchantName = "Best Buy", tags = listOf("audio")),
            receiptText = "BEST BUY\nWH-1000XM5 WIRELESS\nTOTAL 249.99",
        )
        store.purchases.save(
            TestPurchases.purchase(name = "Espresso Machine", merchantName = "Costco", notes = "kitchen counter"),
            receiptText = "COSTCO WHOLESALE\nBREVILLE BAMBINO",
        )
        store.purchases.save(TestPurchases.purchase(name = "Running Shoes", merchantName = "Target"))
    }

    @Test
    fun findsByProductName() {
        seed()
        val results = store.purchases.byIds(store.search.find("headphones"))
        assertEquals(listOf("Sony Headphones"), results.map { it.productName })
    }

    @Test
    fun findsByShopName() {
        seed()
        assertEquals(listOf("Espresso Machine"), store.purchases.byIds(store.search.find("Costco")).map { it.productName })
    }

    @Test
    fun findsByWordsOnlyPresentInTheReceiptText() {
        // Someone remembers the model number printed on the receipt, not the name
        // they gave the purchase.
        seed()
        assertEquals(listOf("Espresso Machine"), store.purchases.byIds(store.search.find("breville")).map { it.productName })
    }

    @Test
    fun findsByNotesAndTags() {
        seed()
        assertEquals(listOf("Espresso Machine"), store.purchases.byIds(store.search.find("counter")).map { it.productName })
        assertEquals(listOf("Sony Headphones"), store.purchases.byIds(store.search.find("audio")).map { it.productName })
    }

    @Test
    fun matchesWhileSomeoneIsStillTyping() {
        seed()
        assertEquals(listOf("Sony Headphones"), store.purchases.byIds(store.search.find("headph")).map { it.productName })
    }

    @Test
    fun requiresEveryWordToMatch() {
        seed()
        assertTrue(store.search.find("sony costco").isEmpty())
        assertEquals(1, store.search.find("sony headphones").size)
    }

    @Test
    fun ignoresAccentsSoPeopleCanTypeWithoutThem() {
        store.purchases.save(TestPurchases.purchase(name = "Café Grinder", merchantName = "Café Supply"))
        assertEquals(1, store.search.find("cafe").size)
    }

    @Test
    fun treatsSearchTextAsWordsNotAsQuerySyntax() {
        // FTS5 has its own expression language. A person typing a quote, a star or
        // the word NEAR must get a search, not a syntax error and not a different query.
        seed()
        val hostile = listOf(
            "\"",
            "headphones\" OR \"",
            "headphones NEAR costco",
            "sony*",
            "headphones AND (costco",
            "' OR 1=1 --",
        )
        hostile.forEach { input ->
            // The point is that each of these searches, rather than throwing or
            // being reinterpreted as an FTS5 expression.
            val results = store.search.find(input)
            assertTrue(results.size <= 3, "unexpected result count for input: $input")
        }
        // Punctuation is dropped, so a stray quote still finds what the person meant.
        assertEquals(1, store.search.find("headphones\"").size)
        assertEquals(1, store.search.find("sony*").size)
        // "OR" is searched for as a word, not honoured as an operator.
        assertTrue(store.search.find("headphones\" OR \"").isEmpty())
    }

    @Test
    fun buildsAPredictableMatchExpression() {
        assertEquals("\"sony\" AND \"headphones\"*", store.search.toMatchExpression("sony headphones"))
        assertEquals("\"OR\"*", store.search.toMatchExpression("OR"))
        assertNull(store.search.toMatchExpression("   "))
        assertNull(store.search.toMatchExpression("!!!"))
    }

    @Test
    fun forgetsAPurchaseThatWasDeleted() {
        seed()
        val shoes = store.purchases.all().first { it.productName == "Running Shoes" }
        store.purchases.delete(shoes.id)
        assertTrue(store.search.find("running").isEmpty())
        assertEquals(2L, store.search.count())
    }

    @Test
    fun reindexesWhenAPurchaseIsRenamed() {
        seed()
        val shoes = store.purchases.all().first { it.productName == "Running Shoes" }
        store.purchases.save(shoes.copy(productName = "Trail Shoes"))
        assertTrue(store.search.find("running").isEmpty())
        assertEquals(1, store.search.find("trail").size)
        assertEquals(3L, store.search.count())
    }
}
