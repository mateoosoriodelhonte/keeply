package app.keeply.documents

import app.keeply.domain.DocumentKind
import app.keeply.domain.Ids
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

/**
 * Where Keeply keeps everything.
 *
 * One directory, laid out predictably, so a person can find their receipts with
 * Finder and back the folder up themselves. Keeply never writes outside it.
 *
 * ```
 * keeply-data/
 *   database/     keeply.db
 *   receipts/     the original imported files, never modified
 *   manuals/
 *   photos/
 *   thumbnails/   derived, safe to delete, regenerated on demand
 *   processed/    derived, cleaned-up images used for OCR
 * ```
 */
public class DataDirectory(public val root: Path) {

    public val database: Path get() = root.resolve("database")
    public val receipts: Path get() = root.resolve("receipts")
    public val manuals: Path get() = root.resolve("manuals")
    public val photos: Path get() = root.resolve("photos")
    public val thumbnails: Path get() = root.resolve("thumbnails")
    public val processed: Path get() = root.resolve("processed")

    /** Everything Keeply derives and can rebuild. Safe to delete to reclaim space. */
    public val derivedDirectories: List<Path> get() = listOf(thumbnails, processed)

    public fun create(): DataDirectory {
        listOf(root, database, receipts, manuals, photos, thumbnails, processed)
            .forEach(Files::createDirectories)
        return this
    }

    public fun directoryFor(kind: DocumentKind): Path = when (kind) {
        DocumentKind.RECEIPT, DocumentKind.WARRANTY -> receipts
        DocumentKind.MANUAL -> manuals
        DocumentKind.PRODUCT_PHOTO -> photos
    }

    /**
     * Builds the path for a new file.
     *
     * The name comes from a generated identifier, never from what the person called
     * the file, so a receipt does not sit in the data folder under a name that says
     * where they shop and what they bought. Files are spread over sub-folders by the
     * first two characters, which keeps directory listings usable once someone has
     * imported a few thousand receipts.
     */
    public fun allocate(kind: DocumentKind, id: String, extension: String): Path {
        require(Ids.isValid(id)) { "Refusing to build a path from an identifier Keeply did not generate" }
        // Rejected rather than sanitised: the extension always comes from a
        // DocumentFormat, so anything else is a caller bug worth surfacing.
        val safeExtension = extension.lowercase(Locale.ROOT)
        require(
            safeExtension.isNotEmpty() &&
                safeExtension.length <= MAX_EXTENSION &&
                safeExtension.all(Char::isLetterOrDigit),
        ) {
            "Refusing to build a path with extension '$extension'"
        }
        return directoryFor(kind).resolve(id.take(SHARD_LENGTH)).resolve("$id.$safeExtension")
    }

    /** The path a stored document's relative path refers to, checked to stay inside the root. */
    public fun resolve(relativePath: String): Path {
        val candidate = root.resolve(relativePath).normalize()
        require(candidate.startsWith(root.normalize())) {
            "Refusing to read outside the Keeply data directory"
        }
        return candidate
    }

    public fun relativise(path: Path): String = root.normalize().relativize(path.normalize()).toString().replace('\\', '/')

    public companion object {
        private const val SHARD_LENGTH = 2
        private const val MAX_EXTENSION = 8

        /**
         * The default location, following each platform's convention. Keeply never
         * scatters files elsewhere on the machine.
         */
        public fun default(): DataDirectory {
            val os = System.getProperty("os.name").lowercase(Locale.ROOT)
            val home = Paths.get(System.getProperty("user.home"))
            val root = when {
                os.contains("mac") || os.contains("darwin") ->
                    home.resolve("Library/Application Support/Keeply")

                os.contains("win") ->
                    Paths.get(System.getenv("LOCALAPPDATA") ?: home.resolve("AppData/Local").toString())
                        .resolve("Keeply")

                else ->
                    Paths.get(System.getenv("XDG_DATA_HOME") ?: home.resolve(".local/share").toString())
                        .resolve("keeply")
            }
            return DataDirectory(root)
        }
    }
}
