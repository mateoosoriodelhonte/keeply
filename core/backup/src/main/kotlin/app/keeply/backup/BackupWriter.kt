package app.keeply.backup

import app.keeply.data.KeeplyStore
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
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** How far a backup has got, for the progress bar. */
public sealed interface BackupProgress {
    public data object Preparing : BackupProgress
    public data class Writing(val filesWritten: Int, val filesTotal: Int) : BackupProgress
    public data class Finished(val path: Path, val byteSize: Long) : BackupProgress
}

/**
 * Writes everything Keeply holds into one portable file.
 *
 * A local-only application has to make this good. If a person's laptop is stolen
 * and their backup does not restore, Keeply has lost their receipts, and no
 * amount of privacy makes up for that.
 *
 * The archive carries the database, every original document, and a manifest with
 * a digest of each file, so a restore can tell whether what it read is what was
 * written.
 */
public class BackupWriter(
    private val directory: DataDirectory,
    private val appVersion: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(BackupWriter::class.java)
    private val json = Json { prettyPrint = true }

    public suspend fun write(store: KeeplyStore, target: Path, onProgress: (BackupProgress) -> Unit = {}): BackupManifest =
        withContext(dispatcher) {
            onProgress(BackupProgress.Preparing)

            val documents = store.documents.all()
            val files = documents.mapNotNull { document ->
                val path = runCatching { directory.resolve(document.relativePath) }.getOrNull()
                if (path == null || !Files.isRegularFile(path)) {
                    // A document whose file has gone missing is worth noting but is not a
                    // reason to refuse the whole backup.
                    log.warn("Skipping {}: its file is missing", document.id)
                    null
                } else {
                    document.relativePath to path
                }
            }

            val entries = mutableListOf<BackupEntry>()
            // Written to a temporary name and moved into place, so an interrupted backup
            // never leaves a file that looks complete but is not.
            val partial = target.resolveSibling("${target.fileName}.part")
            Files.createDirectories(target.toAbsolutePath().parent)

            try {
                ZipOutputStream(Files.newOutputStream(partial)).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED)

                    val databaseFile = directory.database.resolve("keeply.db")
                    if (Files.isRegularFile(databaseFile)) {
                        entries += copyInto(zip, BackupManifest.DATABASE_PATH, databaseFile)
                    }

                    files.forEachIndexed { index, (relativePath, path) ->
                        coroutineContext.ensureActive()
                        onProgress(BackupProgress.Writing(index + 1, files.size))
                        entries += copyInto(zip, BackupManifest.FILES_PREFIX + relativePath, path)
                    }

                    val manifest = BackupManifest(
                        formatVersion = BackupManifest.CURRENT_FORMAT_VERSION,
                        schemaVersion = app.keeply.data.KeeplyDatabaseFactory.schemaVersion,
                        appVersion = appVersion,
                        createdAt = Instant.now().toString(),
                        purchaseCount = store.purchases.count(),
                        documentCount = store.documents.count(),
                        entries = entries,
                    )
                    zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_PATH))
                    zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                    zip.closeEntry()

                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                    onProgress(BackupProgress.Finished(target, Files.size(target)))
                    return@withContext manifest
                }
            } catch (e: IOException) {
                runCatching { Files.deleteIfExists(partial) }
                throw BackupException(
                    "Keeply could not finish writing that backup. The disk may be full, " +
                        "or the folder may not be writable. Nothing has been changed.",
                    e,
                )
            }
        }

    private fun copyInto(zip: ZipOutputStream, entryName: String, source: Path): BackupEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entryName))
        var written = 0L
        Files.newInputStream(source).use { raw ->
            DigestInputStream(raw, digest).use { stream ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    zip.write(buffer, 0, read)
                    written += read
                }
            }
        }
        zip.closeEntry()
        return BackupEntry(entryName, written, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
