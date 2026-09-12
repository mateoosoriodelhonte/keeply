package app.keeply.backup

import java.nio.file.Path

/**
 * Decides whether a path inside an archive is one Keeply is willing to write.
 *
 * Every archive reader that has ever been exploited was exploited here. An entry
 * named `../../.ssh/authorized_keys` will happily be written outside the folder a
 * person chose, by any code that resolves it naively. Keeply refuses the entry
 * instead, and then checks the resolved path as well, because defence that depends
 * on one regex being right is not defence.
 */
internal object ArchivePaths {

    fun validate(rawPath: String, limits: BackupLimits): String {
        if (rawPath.isBlank()) throw BackupException("That backup contains an entry with no name.")
        if (rawPath.length > limits.maxPathLength) {
            throw BackupException("That backup contains a file path far longer than any Keeply writes.")
        }
        if (rawPath.startsWith("/") || rawPath.startsWith("\\")) {
            throw BackupException("That backup contains an absolute file path, which Keeply will not write.")
        }
        if (DRIVE_LETTER.containsMatchIn(rawPath)) {
            throw BackupException("That backup contains an absolute file path, which Keeply will not write.")
        }
        if (rawPath.contains('\\')) {
            throw BackupException("That backup contains a file path Keeply does not recognise as safe.")
        }
        if (rawPath.contains('\u0000')) {
            throw BackupException("That backup contains a file path with a null character in it.")
        }

        val segments = rawPath.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) {
            throw BackupException(
                "That backup tries to write outside the folder you chose. Keeply has not restored anything.",
            )
        }
        if (segments.any { it.trim() != it }) {
            throw BackupException("That backup contains a file path with padded names, which Keeply will not write.")
        }
        return rawPath
    }

    /**
     * Resolves an entry under [root] and proves the result really is under it.
     *
     * The belt to [validate]'s braces: symlinks, case-insensitive filesystems and
     * unicode normalisation can all turn a path that looked fine into one that is
     * not, and only the resolved path tells the truth.
     */
    fun resolveWithin(root: Path, rawPath: String, limits: BackupLimits): Path {
        val safe = validate(rawPath, limits)
        val base = root.toAbsolutePath().normalize()
        val resolved = base.resolve(safe).normalize()
        if (!resolved.startsWith(base)) {
            throw BackupException(
                "That backup tries to write outside the folder you chose. Keeply has not restored anything.",
            )
        }
        return resolved
    }

    /** Only the places Keeply itself writes. Anything else in an archive is refused. */
    fun isExpected(rawPath: String): Boolean = rawPath == BackupManifest.MANIFEST_PATH ||
        rawPath == BackupManifest.DATABASE_PATH ||
        rawPath.startsWith(BackupManifest.FILES_PREFIX)

    private val DRIVE_LETTER = Regex("""^[A-Za-z]:""")
}
