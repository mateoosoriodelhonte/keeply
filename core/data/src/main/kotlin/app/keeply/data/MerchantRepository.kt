package app.keeply.data

import app.keeply.data.sql.KeeplyDatabase
import app.keeply.domain.FieldConfidence
import app.keeply.domain.Merchant
import app.keeply.domain.MerchantId
import app.keeply.domain.MerchantMatch
import java.time.Instant

/**
 * Shops the person buys from, and the return windows they have saved for them.
 *
 * Matching receipt text against saved merchants happens here because it needs the
 * whole list, and because recognising a shop is the thing that makes a saved return
 * rule useful at all.
 */
public class MerchantRepository internal constructor(private val database: KeeplyDatabase) {
    private val queries get() = database.merchantQueries

    public fun all(): List<Merchant> = queries.selectAll().executeAsList().map(Mappers::toMerchant)

    public fun get(id: MerchantId): Merchant? = queries.selectById(id.value).executeAsOneOrNull()?.let(Mappers::toMerchant)

    public fun findByName(name: String): Merchant? =
        queries.selectByMatchKey(Merchant.normaliseName(name)).executeAsOneOrNull()?.let(Mappers::toMerchant)

    public fun save(merchant: Merchant, now: Instant = Instant.now()) {
        val (kind, days, until) = Mappers.returnPolicyColumns(merchant.defaultReturnPolicy)
        queries.upsert(
            id = merchant.id.value,
            name = merchant.name,
            match_key = merchant.matchKey,
            aliases = merchant.aliases.joinToString("\n"),
            return_policy_kind = kind.takeIf { it != "UNKNOWN" },
            return_policy_days = days,
            return_policy_until = until,
            notes = merchant.notes,
            created_at = now.toEpochMilli(),
            updated_at = now.toEpochMilli(),
        )
    }

    public fun delete(id: MerchantId) {
        queries.deleteById(id.value)
    }

    public fun count(): Long = queries.countAll().executeAsOne()

    /**
     * Finds the saved merchant that best explains a line of receipt text.
     *
     * An exact match on a normalised name is confident. A saved name appearing
     * inside a longer line, which is what a till header usually looks like, is
     * uncertain and gets shown for review rather than applied quietly.
     */
    public fun match(receiptText: String): MerchantMatch? {
        val merchants = all()
        if (merchants.isEmpty()) return null

        val lines = receiptText.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .take(HEADER_LINES)
            .toList()

        for (line in lines) {
            val normalised = Merchant.normaliseName(line)
            if (normalised.isBlank()) continue
            merchants.firstOrNull { merchant -> merchant.matchKeys.any { it == normalised } }?.let {
                return MerchantMatch(it, FieldConfidence.CONFIDENT, line)
            }
        }

        for (line in lines) {
            val normalised = Merchant.normaliseName(line)
            if (normalised.isBlank()) continue
            merchants.firstOrNull { merchant ->
                merchant.matchKeys.any { key -> key.length >= MIN_SUBSTRING && normalised.contains(key) }
            }?.let {
                return MerchantMatch(it, FieldConfidence.UNCERTAIN, line)
            }
        }
        return null
    }

    private companion object {
        /** A shop name is near the top of a receipt, not buried in the item list. */
        const val HEADER_LINES = 8

        /** Short names produce nonsense substring matches. */
        const val MIN_SUBSTRING = 4
    }
}
