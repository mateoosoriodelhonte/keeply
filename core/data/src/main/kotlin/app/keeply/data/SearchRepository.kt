package app.keeply.data

import app.keeply.data.sql.KeeplyDatabase
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId

/**
 * Full-text search over everything a person might remember about a purchase:
 * what it was, where they bought it, what they wrote about it, and the text
 * Keeply read off the receipt.
 *
 * The index is maintained explicitly rather than by triggers, because the receipt
 * text lives in a different table and arrives later, once OCR has finished.
 */
public class SearchRepository internal constructor(private val database: KeeplyDatabase) {
    private val queries get() = database.searchQueries

    /**
     * Finds purchases matching typed text, most relevant first.
     *
     * The query is rebuilt from the words the person typed rather than passed
     * through. FTS5 has its own expression syntax, and a stray quote or a word like
     * `NEAR` would otherwise either break the search or mean something unintended.
     * Each word becomes a quoted term, and the last one gets a prefix match so
     * results appear while someone is still typing.
     */
    public fun find(text: String): List<PurchaseId> {
        val expression = toMatchExpression(text) ?: return emptyList()
        // FTS5 columns are nullable in SQLite's type system, so the id is mapped out
        // explicitly rather than through the generated row class.
        // FTS5 columns are nullable in SQLite's type system, so the id is mapped out
        // explicitly rather than through the generated row class.
        return queries.search(expression) { id -> id.orEmpty() }
            .executeAsList()
            .filter(String::isNotEmpty)
            .map(::PurchaseId)
    }

    /** Rewrites this purchase's index entry. Called inside the transaction that saves it. */
    public fun reindex(purchase: Purchase, receiptText: String?) {
        queries.deleteFor(purchase.id.value)
        queries.insert(
            purchase_id = purchase.id.value,
            product_name = purchase.productName,
            merchant_name = purchase.merchantName.orEmpty(),
            notes = purchase.notes.orEmpty(),
            tags = purchase.tags.joinToString(" "),
            serial_number = purchase.serialNumber.orEmpty(),
            receipt_text = receiptText.orEmpty(),
        )
    }

    public fun remove(id: PurchaseId) {
        queries.deleteFor(id.value)
    }

    public fun clear() {
        queries.deleteAll()
    }

    public fun count(): Long = queries.countAll().executeAsOne()

    internal fun toMatchExpression(text: String): String? {
        val terms = text.split(WORD_SEPARATOR)
            .map { word -> word.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null

        return terms.mapIndexed { index, term ->
            val quoted = "\"$term\""
            // Prefix-match the final word so search updates as someone types.
            if (index == terms.lastIndex && term.length >= MIN_PREFIX_LENGTH) "$quoted*" else quoted
        }.joinToString(" AND ")
    }

    private companion object {
        val WORD_SEPARATOR = Regex("\\s+")
        const val MIN_PREFIX_LENGTH = 2
    }
}
