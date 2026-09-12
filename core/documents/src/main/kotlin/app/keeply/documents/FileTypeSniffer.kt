package app.keeply.documents

import app.keeply.domain.DocumentFormat

/**
 * Works out what a file actually is by looking at its first bytes.
 *
 * The extension is a claim made by whoever produced the file, and an import
 * pipeline that trusts it will happily hand a renamed executable to a PDF parser.
 * Keeply reads the magic bytes instead and refuses anything it does not recognise.
 */
public object FileTypeSniffer {

    /** Enough bytes to identify every format Keeply accepts. */
    public const val HEADER_BYTES: Int = 32

    public fun detect(header: ByteArray): DocumentFormat? = when {
        startsWith(header, PDF_MAGIC) -> DocumentFormat.PDF
        startsWith(header, PNG_MAGIC) -> DocumentFormat.PNG
        startsWith(header, JPEG_MAGIC) -> DocumentFormat.JPEG
        else -> null
    }

    /**
     * True when the extension matches what the bytes say.
     *
     * A mismatch is not fatal: people rename files, and a JPEG called `.png` is a
     * harmless mistake. Keeply uses the bytes and records the disagreement rather
     * than refusing the import.
     */
    public fun extensionAgrees(fileName: String, format: DocumentFormat): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (format) {
            DocumentFormat.JPEG -> extension in setOf("jpg", "jpeg", "jpe")
            DocumentFormat.PNG -> extension == "png"
            DocumentFormat.PDF -> extension == "pdf"
        }
    }

    private fun startsWith(header: ByteArray, magic: ByteArray): Boolean {
        if (header.size < magic.size) return false
        return magic.indices.all { header[it] == magic[it] }
    }

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
}
