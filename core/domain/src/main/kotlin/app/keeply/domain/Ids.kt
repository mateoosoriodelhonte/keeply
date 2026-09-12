package app.keeply.domain

import java.security.SecureRandom
import java.util.Locale

/**
 * Keeply identifies everything by a generated opaque id rather than by anything a
 * person typed. Filenames on disk are derived from these, so a receipt never sits in
 * the data directory under a name that leaks what it is.
 */
public object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val LENGTH = 26
    private val random = SecureRandom()

    public fun generate(): String {
        val chars = CharArray(LENGTH)
        val bytes = ByteArray(LENGTH)
        random.nextBytes(bytes)
        for (i in 0 until LENGTH) {
            chars[i] = ALPHABET[(bytes[i].toInt() and 0xFF) % ALPHABET.length]
        }
        return String(chars)
    }

    /** True when [value] looks like an id Keeply generated. Used to reject hostile paths. */
    public fun isValid(value: String): Boolean = value.length == LENGTH && value.all { it in ALPHABET }

    internal fun normalise(value: String): String = value.lowercase(Locale.ROOT).trim()
}

@JvmInline
public value class PurchaseId(public val value: String) {
    init {
        require(value.isNotBlank()) { "PurchaseId must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        public fun new(): PurchaseId = PurchaseId(Ids.generate())
    }
}

@JvmInline
public value class MerchantId(public val value: String) {
    init {
        require(value.isNotBlank()) { "MerchantId must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        public fun new(): MerchantId = MerchantId(Ids.generate())
    }
}

@JvmInline
public value class DocumentId(public val value: String) {
    init {
        require(value.isNotBlank()) { "DocumentId must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        public fun new(): DocumentId = DocumentId(Ids.generate())
    }
}

@JvmInline
public value class CategoryId(public val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        public fun new(): CategoryId = CategoryId(Ids.generate())
    }
}
