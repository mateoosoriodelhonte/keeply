package app.keeply.backup

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Builds the archives an attacker would send.
 *
 * The reader's job is to refuse these, so the tests have to be able to make them.
 * Nothing here is used by the application.
 */
internal object ArchiveEditor {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Copies an archive, replacing its manifest with a modified one. */
    fun rewriteManifest(source: Path, target: Path, transform: (BackupManifest) -> BackupManifest) {
        ZipFile(source.toFile()).use { zip ->
            val original = zip.getInputStream(zip.getEntry(BackupManifest.MANIFEST_PATH)).use {
                json.decodeFromString(BackupManifest.serializer(), it.readBytes().decodeToString())
            }
            val replacement = json.encodeToString(BackupManifest.serializer(), transform(original))

            ZipOutputStream(Files.newOutputStream(target)).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    out.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == BackupManifest.MANIFEST_PATH) {
                        out.write(replacement.toByteArray())
                    } else {
                        zip.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
    }

    /** Copies an archive and adds one extra entry, under any name at all. */
    fun withExtraEntry(source: Path, target: Path, name: String, content: ByteArray) {
        ZipFile(source.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(target)).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
    }

    /** An archive with only the entries given, and no manifest unless one is included. */
    fun of(target: Path, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(target)).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
    }

    /** A minimal valid manifest, for archives built from scratch. */
    fun manifestBytes(entries: List<BackupEntry> = emptyList()): ByteArray = json.encodeToString(
        BackupManifest.serializer(),
        BackupManifest(
            formatVersion = BackupManifest.CURRENT_FORMAT_VERSION,
            schemaVersion = app.keeply.data.KeeplyDatabaseFactory.schemaVersion,
            appVersion = "1.0.0",
            createdAt = "2026-09-12T10:00:00Z",
            purchaseCount = 0,
            documentCount = 0,
            entries = entries,
        ),
    ).toByteArray()

    /**
     * Writes a ZIP by hand, so the tests can build archives Java's own writer
     * refuses to produce.
     *
     * `ZipOutputStream` rejects a duplicate entry name, which is exactly why an
     * attacker would not use it. Entries are stored uncompressed, which keeps this
     * short and is perfectly valid ZIP.
     */
    fun rawZip(target: Path, entries: List<Pair<String, ByteArray>>) {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        entries.forEach { (name, content) ->
            offsets += out.size()
            val nameBytes = name.toByteArray()
            val crc = java.util.zip.CRC32().apply { update(content) }.value

            out.writeInt(LOCAL_HEADER)
            out.writeShort(20)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeInt(crc.toInt())
            out.writeInt(content.size)
            out.writeInt(content.size)
            out.writeShort(nameBytes.size)
            out.writeShort(0)
            out.write(nameBytes)
            out.write(content)
        }

        val centralStart = out.size()
        entries.forEachIndexed { index, (name, content) ->
            val nameBytes = name.toByteArray()
            val crc = java.util.zip.CRC32().apply { update(content) }.value

            out.writeInt(CENTRAL_HEADER)
            out.writeShort(20)
            out.writeShort(20)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeInt(crc.toInt())
            out.writeInt(content.size)
            out.writeInt(content.size)
            out.writeShort(nameBytes.size)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeInt(0)
            out.writeInt(offsets[index])
            out.write(nameBytes)
        }

        out.writeInt(END_OF_CENTRAL)
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(entries.size)
        out.writeShort(entries.size)
        out.writeInt(out.size() - centralStart)
        out.writeInt(centralStart)
        out.writeShort(0)

        Files.write(target, out.toByteArray())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL = 0x06054b50

    /** Highly compressible content, for testing the expansion ratio guard. */
    fun compressibleBytes(size: Int): ByteArray = ByteArray(size)

    fun bytes(text: String): ByteArray = ByteArrayOutputStream().apply { write(text.toByteArray()) }.toByteArray()
}
