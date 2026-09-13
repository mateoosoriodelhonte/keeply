package app.keeply.services

import app.keeply.data.KeeplyStore
import app.keeply.documents.DataDirectory
import app.keeply.domain.DocumentKind
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** One line on the storage screen. */
public data class StorageLine(
    val label: String,
    val bytes: Long,
    val fileCount: Long,
    /** True when Keeply can rebuild this, so deleting it loses nothing. */
    val rebuildable: Boolean,
)

public data class StorageReport(val lines: List<StorageLine>, val totalBytes: Long) {
    public val rebuildableBytes: Long get() = lines.filter { it.rebuildable }.sumOf { it.bytes }
}

/**
 * What Keeply is using, and what can safely be cleared.
 *
 * The distinction the screen rests on: thumbnails and cleaned-up images are
 * derived and can be rebuilt from the originals, while receipts, manuals and
 * photographs cannot be rebuilt from anything. Only the first kind is ever
 * offered for deletion, and a test asserts the second never appears in that list.
 */
public class StorageService(private val store: KeeplyStore, private val directory: DataDirectory) {
    private val log = LoggerFactory.getLogger(StorageService::class.java)

    public fun report(): StorageReport {
        val usage = store.documents.usage().associateBy { it.kind }
        val lines = buildList {
            add(
                StorageLine(
                    "Receipts",
                    usage[DocumentKind.RECEIPT]?.totalBytes ?: 0,
                    usage[DocumentKind.RECEIPT]?.fileCount ?: 0,
                    rebuildable = false,
                ),
            )
            add(
                StorageLine(
                    "Warranty documents",
                    usage[DocumentKind.WARRANTY]?.totalBytes ?: 0,
                    usage[DocumentKind.WARRANTY]?.fileCount ?: 0,
                    rebuildable = false,
                ),
            )
            add(
                StorageLine(
                    "Manuals",
                    usage[DocumentKind.MANUAL]?.totalBytes ?: 0,
                    usage[DocumentKind.MANUAL]?.fileCount ?: 0,
                    rebuildable = false,
                ),
            )
            add(
                StorageLine(
                    "Photos",
                    usage[DocumentKind.PRODUCT_PHOTO]?.totalBytes ?: 0,
                    usage[DocumentKind.PRODUCT_PHOTO]?.fileCount ?: 0,
                    rebuildable = false,
                ),
            )
            add(measure("Database", directory.database, rebuildable = false))
            add(measure("Thumbnails", directory.thumbnails, rebuildable = true))
            add(measure("Processed images", directory.processed, rebuildable = true))
        }
        return StorageReport(lines, lines.sumOf { it.bytes })
    }

    /**
     * Deletes only what Keeply can rebuild.
     *
     * There is no code path here that can reach a receipt, a manual or a photo.
     * Removing an original is a separate, deliberate action on that one purchase.
     */
    public fun clearRebuildable(): Long {
        var freed = 0L
        directory.derivedDirectories.forEach { folder ->
            freed += deleteContents(folder)
        }
        log.info("Cleared {} bytes of rebuildable data", freed)
        return freed
    }

    private fun measure(label: String, folder: Path, rebuildable: Boolean): StorageLine {
        if (!Files.isDirectory(folder)) return StorageLine(label, 0, 0, rebuildable)
        var bytes = 0L
        var count = 0L
        Files.walk(folder).use { paths ->
            paths.filter(Files::isRegularFile).forEach { file ->
                bytes += runCatching { Files.size(file) }.getOrDefault(0L)
                count++
            }
        }
        return StorageLine(label, bytes, count, rebuildable)
    }

    private fun deleteContents(folder: Path): Long {
        if (!Files.isDirectory(folder)) return 0L
        var freed = 0L
        Files.walk(folder).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path == folder) return@forEach
                if (Files.isRegularFile(path)) freed += runCatching { Files.size(path) }.getOrDefault(0L)
                runCatching { Files.deleteIfExists(path) }
            }
        }
        Files.createDirectories(folder)
        return freed
    }
}
