package app.keeply.domain

import java.time.Instant

/** What a stored file is to the person who imported it. */
public enum class DocumentKind {
    RECEIPT,
    WARRANTY,
    MANUAL,
    PRODUCT_PHOTO,
    ;

    public val isSourceEvidence: Boolean get() = this == RECEIPT || this == WARRANTY
}

/** The file formats Keeply accepts. Anything else is refused at the door. */
public enum class DocumentFormat(public val mediaType: String, public val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf"),
    ;

    public val isImage: Boolean get() = this != PDF

    public companion object {
        public fun fromMediaType(mediaType: String): DocumentFormat? =
            entries.firstOrNull { it.mediaType.equals(mediaType, ignoreCase = true) }
    }
}

/**
 * A file Keeply has taken responsibility for.
 *
 * The original bytes are never modified. Anything Keeply derives from them, such as
 * a cleaned-up image or OCR text, is stored separately and can be regenerated.
 */
public data class StoredDocument(
    val id: DocumentId,
    val kind: DocumentKind,
    val format: DocumentFormat,
    /** Path relative to the Keeply data directory. Always a generated name. */
    val relativePath: String,
    val byteSize: Long,
    /** SHA-256 of the original bytes, used for exact duplicate detection and backup integrity. */
    val sha256: String,
    val importedAt: Instant,
    /** The name the file had when it was imported, kept only so the person recognises it. */
    val originalFileName: String?,
    val pageCount: Int? = null,
) {
    init {
        require(byteSize >= 0) { "byteSize must not be negative" }
        require(sha256.length == SHA256_HEX_LENGTH) { "sha256 must be a 64-character hex digest" }
    }

    public companion object {
        public const val SHA256_HEX_LENGTH: Int = 64
    }
}

/**
 * Text Keeply derived from a document, kept strictly apart from the document itself.
 *
 * Re-running OCR replaces this. It never touches [StoredDocument].
 */
public data class DocumentText(
    val documentId: DocumentId,
    val text: String,
    val source: FieldSource,
    /** Mean per-word confidence reported by the OCR engine, 0..100. Null for embedded PDF text. */
    val meanConfidence: Double?,
    val extractedAt: Instant,
    val engine: String,
    val durationMillis: Long,
) {
    init {
        require(meanConfidence == null || meanConfidence in 0.0..100.0) {
            "meanConfidence must be a percentage, was $meanConfidence"
        }
    }
}
