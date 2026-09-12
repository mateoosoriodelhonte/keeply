package app.keeply.backup

import app.keeply.data.KeeplyDatabaseFactory
import app.keeply.documents.DataDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/** What to do about data that is already there. */
public enum class RestoreMode {
    /** Refuse if the folder already holds a Keeply library. The default. */
    ONLY_IF_EMPTY,

    /** Replace what is there. Only ever reached after the person confirms. */
    REPLACE_EXISTING,
}

public sealed interface RestoreProgress {
    public data object Checking : RestoreProgress
    public data class Restoring(val filesRestored: Int, val filesTotal: Int) : RestoreProgress
    public data class Finished(val purchaseCount: Long, val documentCount: Long) : RestoreProgress
}

/**
 * Reads a backup archive, assuming it is hostile until proven otherwise.
 *
 * A backup file can come from anywhere, and the attacks against archive readers
 * are cheap to mount: paths that escape the destination, entries that expand to
 * fill a disk, duplicate names that overwrite what was just written.
 *
 * Everything is checked before anything is written, and the write itself goes to a
 * staging folder that is only moved into place once every file has been extracted
 * and its digest verified. A restore either happens completely or not at all.
 */
public class BackupReader(
    private val limits: BackupLimits = BackupLimits.DEFAULT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(BackupReader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /** Reads the manifest and checks the archive, without extracting anything. */
    public fun inspect(archive: Path): BackupSummary {
        if (!Files.isRegularFile(archive)) {
            throw BackupException("Keeply could not find that backup file.")
        }
        openArchive(archive).use { zip ->
            val manifest = readManifest(zip)
            val problem = when {
                manifest.formatVersion > BackupManifest.CURRENT_FORMAT_VERSION ->
                    "That backup was made by a newer version of Keeply. Update Keeply to restore it."

                manifest.schemaVersion > KeeplyDatabaseFactory.schemaVersion ->
                    "That backup holds data from a newer version of Keeply. Update Keeply to restore it."

                else -> null
            }
            validateEntries(zip)
            return BackupSummary(
                manifest = manifest,
                totalBytes = manifest.entries.sumOf { it.byteSize },
                restorable = problem == null,
                problem = problem,
            )
        }
    }

    /**
     * Restores an archive into [directory].
     *
     * Extraction goes to a staging folder beside the destination. Only once every
     * file is written and verified is the existing library replaced, so a restore
     * that fails half way leaves the person exactly as they were.
     */
    public suspend fun restore(
        archive: Path,
        directory: DataDirectory,
        mode: RestoreMode = RestoreMode.ONLY_IF_EMPTY,
        onProgress: (RestoreProgress) -> Unit = {},
    ): BackupManifest = withContext(dispatcher) {
        onProgress(RestoreProgress.Checking)
        val summary = inspect(archive)
        summary.problem?.let { throw BackupException(it) }

        val existing = directory.database.resolve(KeeplyDatabaseFactory.DATABASE_FILE_NAME)
        if (mode == RestoreMode.ONLY_IF_EMPTY && Files.exists(existing)) {
            throw BackupException(
                "There is already a Keeply library in that folder. " +
                    "Restoring would replace it, so Keeply has stopped and changed nothing.",
            )
        }

        val staging = directory.root.resolveSibling("${directory.root.fileName}.restoring")
        runCatching { staging.toFile().deleteRecursively() }
        Files.createDirectories(staging)

        try {
            openArchive(archive).use { zip ->
                val entries = validateEntries(zip)
                val expected = summary.manifest.entries.associateBy { it.path }
                var restored = 0

                entries.forEach { entry ->
                    coroutineContext.ensureActive()
                    if (entry.name == BackupManifest.MANIFEST_PATH) return@forEach

                    val destination = ArchivePaths.resolveWithin(staging, destinationFor(entry.name), limits)
                    Files.createDirectories(destination.parent)
                    val digest = extract(zip, entry, destination)

                    expected[entry.name]?.let { declared ->
                        if (declared.sha256 != digest) {
                            throw BackupException(
                                "That backup is damaged: ${entry.name} does not match what the backup says it should be. " +
                                    "Nothing has been restored.",
                            )
                        }
                    }
                    restored++
                    onProgress(RestoreProgress.Restoring(restored, entries.size))
                }
            }

            swapIntoPlace(staging, directory)
            onProgress(
                RestoreProgress.Finished(summary.manifest.purchaseCount, summary.manifest.documentCount),
            )
            summary.manifest
        } catch (e: BackupException) {
            runCatching { staging.toFile().deleteRecursively() }
            throw e
        } catch (e: IOException) {
            runCatching { staging.toFile().deleteRecursively() }
            throw BackupException("Keeply could not read that backup. It may be damaged. Nothing has been restored.", e)
        }
    }

    /**
     * Where an archive entry belongs in the data directory.
     *
     * Documents are stored under a `files/` prefix inside the archive so the layout
     * is obvious to anyone who opens it, and that prefix is removed on the way out.
     * The name is still validated before and after this, so stripping a prefix
     * cannot become a way to escape.
     */
    private fun destinationFor(entryName: String): String = if (entryName.startsWith(BackupManifest.FILES_PREFIX)) {
        entryName.removePrefix(BackupManifest.FILES_PREFIX)
    } else {
        entryName
    }

    private fun openArchive(archive: Path): ZipFile = try {
        ZipFile(archive.toFile())
    } catch (e: IOException) {
        throw BackupException("That file is not a Keeply backup, or it is damaged.", e)
    }

    private fun readManifest(zip: ZipFile): BackupManifest {
        val entry = zip.getEntry(BackupManifest.MANIFEST_PATH)
            ?: throw BackupException("That file is not a Keeply backup: it has no manifest.")
        if (entry.size > MANIFEST_MAX_BYTES) {
            throw BackupException("That backup's manifest is implausibly large, so Keeply will not read it.")
        }
        val text = zip.getInputStream(entry).use { it.readNBytes(MANIFEST_MAX_BYTES.toInt()) }.decodeToString()
        return try {
            json.decodeFromString(BackupManifest.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("That backup's manifest could not be read, so Keeply will not restore it.", e)
        }
    }

    /**
     * Checks every entry before any of them is written.
     *
     * Order matters: a partial extraction that stops at a bad entry has already
     * written the good ones.
     */
    private fun validateEntries(zip: ZipFile): List<ZipEntry> {
        val entries = zip.entries().asSequence().toList()
        if (entries.size > limits.maxEntries) {
            throw BackupException("That backup contains far more files than any Keeply library.")
        }

        val seen = mutableSetOf<String>()
        var totalUncompressed = 0L
        var totalCompressed = 0L

        entries.forEach { entry ->
            if (entry.isDirectory) return@forEach
            ArchivePaths.validate(entry.name, limits)

            if (!ArchivePaths.isExpected(entry.name)) {
                throw BackupException(
                    "That backup contains a file Keeply did not put there (${entry.name}), so it will not be restored.",
                )
            }
            if (!seen.add(entry.name)) {
                // Two entries with one name means the second overwrites the first,
                // after the first has already been checked. Recent JDKs reject such
                // an archive before this runs, but that is a hardening measure rather
                // than a guarantee of the format, so the check stays.
                throw BackupException("That backup lists the same file twice, so Keeply will not restore it.")
            }

            val size = entry.size
            if (size < 0) {
                throw BackupException("That backup does not say how large its files are, so Keeply will not restore it.")
            }
            if (size > limits.maxEntryBytes) {
                throw BackupException("That backup contains a single file larger than Keeply will ever write.")
            }
            totalUncompressed += size
            totalCompressed += entry.compressedSize.coerceAtLeast(1)
        }

        if (totalUncompressed > limits.maxTotalUncompressedBytes) {
            throw BackupException("That backup would expand to more data than Keeply will restore.")
        }
        if (totalCompressed > 0 && totalUncompressed.toDouble() / totalCompressed > limits.maxCompressionRatio) {
            throw BackupException(
                "That file expands far more than any real backup does, so Keeply will not open it.",
            )
        }
        return entries.filterNot { it.isDirectory }
    }

    /** Copies one entry out, bounded by the declared size as well as the limit. */
    private fun extract(zip: ZipFile, entry: ZipEntry, destination: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        zip.getInputStream(entry).use { input ->
            Files.newOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    // The declared size was checked; this checks the actual bytes,
                    // because a zip header can lie about both.
                    if (written > limits.maxEntryBytes || written > entry.size) {
                        throw BackupException(
                            "That backup contains a file larger than it claims, so Keeply has stopped.",
                        )
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Moves the staged library into place.
     *
     * The existing library is moved aside rather than deleted, and only removed once
     * the new one is in place.
     */
    private fun swapIntoPlace(staging: Path, directory: DataDirectory) {
        val root = directory.root
        val displaced = root.resolveSibling("${root.fileName}.previous")
        runCatching { displaced.toFile().deleteRecursively() }

        if (Files.exists(root)) {
            Files.move(root, displaced, StandardCopyOption.ATOMIC_MOVE)
        }
        try {
            Files.move(staging, root, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // Put the person's library back exactly where it was.
            if (Files.exists(displaced)) Files.move(displaced, root, StandardCopyOption.ATOMIC_MOVE)
            throw BackupException("Keeply could not put the restored library in place. Nothing has been changed.", e)
        }
        runCatching { displaced.toFile().deleteRecursively() }
        directory.create()
        log.info("Restored a backup into {}", root)
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MANIFEST_MAX_BYTES = 64L * 1024 * 1024
    }
}
