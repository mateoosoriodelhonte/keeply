package app.keeply.documents

/**
 * Ceilings applied before Keeply decodes anything.
 *
 * An imported file is untrusted input. A 200-byte PNG can claim to be 60,000 by
 * 60,000 pixels and exhaust memory the moment something decodes it, and a PDF can
 * contain thousands of pages. These are checked first, from the file's metadata,
 * so a hostile file is refused rather than processed.
 */
public data class ImportLimits(
    val maxFileBytes: Long = 64L * 1024 * 1024,
    val maxPdfPages: Int = 200,
    /** Roughly a 90-megapixel image: far beyond any phone, far below a decompression bomb. */
    val maxImagePixels: Long = 90_000_000L,
    val maxImageDimension: Int = 30_000,
) {
    public companion object {
        public val DEFAULT: ImportLimits = ImportLimits()
    }
}

/** Why Keeply would not take a file, in words a person can act on. */
public sealed interface ImportRejection {
    public val message: String

    public data class NotAFile(override val message: String) : ImportRejection
    public data class Unreadable(override val message: String) : ImportRejection
    public data class UnsupportedType(override val message: String) : ImportRejection
    public data class TooLarge(override val message: String) : ImportRejection
    public data class TooManyPages(override val message: String) : ImportRejection
    public data class Damaged(override val message: String) : ImportRejection
    public data class PasswordProtected(override val message: String) : ImportRejection
    public data class NoSpace(override val message: String) : ImportRejection
}

/** Raised when an import cannot proceed. Carries wording meant for the screen. */
public class ImportException(public val rejection: ImportRejection) : Exception(rejection.message)
