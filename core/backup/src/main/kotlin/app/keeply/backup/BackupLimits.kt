package app.keeply.backup

/**
 * Ceilings applied while reading an archive.
 *
 * A backup file can come from anywhere: a shared drive, a download, someone
 * else's machine. It is untrusted input, and the classic attacks against archive
 * readers are cheap to mount and expensive to suffer.
 */
public data class BackupLimits(
    /** Total uncompressed bytes. Stops an archive that expands to fill the disk. */
    val maxTotalUncompressedBytes: Long = 20L * 1024 * 1024 * 1024,
    /** One entry's uncompressed size. */
    val maxEntryBytes: Long = 1L * 1024 * 1024 * 1024,
    val maxEntries: Int = 200_000,
    /**
     * Largest allowed ratio of uncompressed to compressed bytes.
     *
     * Ordinary receipts and photographs are already compressed and sit near 1:1.
     * A thousandfold expansion is not a backup, it is a zip bomb.
     */
    val maxCompressionRatio: Double = 200.0,
    val maxPathLength: Int = 255,
) {
    public companion object {
        public val DEFAULT: BackupLimits = BackupLimits()
    }
}
