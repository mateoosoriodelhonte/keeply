package app.keeply.documents

import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.StoredDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream

/** A file Keeply accepted, plus anything it learned on the way in. */
public data class ImportedDocument(
    val document: StoredDocument,
    /** Text already inside a PDF. When present, OCR is skipped entirely. */
    val embeddedText: String?,
    /** True when the file's extension disagreed with its actual contents. */
    val extensionMismatched: Boolean,
)

/**
 * Takes a file Keeply has been handed and makes it Keeply's responsibility.
 *
 * Everything imported is untrusted. The order of checks matters: size and type are
 * established from metadata and the first few bytes, before anything decodes the
 * file, so a decompression bomb or a renamed executable is refused rather than
 * processed.
 *
 * The original is copied, never moved and never modified. The person keeps their
 * file exactly where it was.
 */
public class DocumentImporter(
    private val directory: DataDirectory,
    private val limits: ImportLimits = ImportLimits.DEFAULT,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = LoggerFactory.getLogger(DocumentImporter::class.java)

    public suspend fun import(source: Path, kind: DocumentKind): ImportedDocument = withContext(dispatcher) { importBlocking(source, kind) }

    public fun importBlocking(source: Path, kind: DocumentKind): ImportedDocument {
        val attributes = inspect(source)
        val header = readHeader(source)
        val format = FileTypeSniffer.detect(header) ?: throw ImportException(
            ImportRejection.UnsupportedType(
                "Keeply takes photos and PDFs of receipts: JPG, PNG or PDF. " +
                    "That file is something else.",
            ),
        )

        if (attributes.size > limits.maxFileBytes) {
            throw ImportException(
                ImportRejection.TooLarge(
                    "That file is ${describeSize(attributes.size)}, and Keeply takes up to " +
                        "${describeSize(limits.maxFileBytes)}. Try exporting it at a smaller size.",
                ),
            )
        }

        // Checked before any pixels or pages are decoded.
        val pageCount = when {
            format.isImage -> {
                guardImageDimensions(source)
                null
            }

            else -> null
        }

        val summary = if (format == DocumentFormat.PDF) {
            PdfDocument.summarise(source.toFile(), limits).also {
                if (it.isEncrypted) {
                    throw ImportException(
                        ImportRejection.PasswordProtected(
                            "That PDF is password protected, so Keeply cannot read it. " +
                                "Save an unprotected copy and import that instead.",
                        ),
                    )
                }
            }
        } else {
            null
        }

        val id = DocumentId.new()
        val destination = directory.allocate(kind, id.value, format.extension)
        val digest = copyAndDigest(source, destination)

        val document = StoredDocument(
            id = id,
            kind = kind,
            format = format,
            relativePath = directory.relativise(destination),
            byteSize = attributes.size,
            sha256 = digest,
            importedAt = Instant.now(clock),
            originalFileName = source.fileName?.toString()?.take(MAX_NAME_LENGTH),
            pageCount = summary?.pageCount ?: pageCount,
        )
        log.info("Imported a {} as {}", format, document.id)

        return ImportedDocument(
            document = document,
            embeddedText = summary?.embeddedText,
            extensionMismatched = source.fileName?.toString()
                ?.let { !FileTypeSniffer.extensionAgrees(it, format) } ?: false,
        )
    }

    private data class SourceAttributes(val size: Long)

    private fun inspect(source: Path): SourceAttributes {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS) && !Files.exists(source)) {
            throw ImportException(ImportRejection.NotAFile("Keeply could not find that file."))
        }
        if (Files.isDirectory(source)) {
            throw ImportException(
                ImportRejection.NotAFile("That is a folder. Drop the receipt files themselves."),
            )
        }
        if (!Files.isRegularFile(source)) {
            throw ImportException(
                ImportRejection.NotAFile("Keeply can only import ordinary files."),
            )
        }
        if (!Files.isReadable(source)) {
            throw ImportException(
                ImportRejection.Unreadable(
                    "Keeply is not allowed to read that file. Check its permissions, or copy it somewhere else first.",
                ),
            )
        }
        return SourceAttributes(Files.size(source))
    }

    private fun readHeader(source: Path): ByteArray = Files.newInputStream(source).use { stream ->
        val header = ByteArray(FileTypeSniffer.HEADER_BYTES)
        val read = stream.readNBytes(header, 0, header.size)
        header.copyOf(read.coerceAtLeast(0))
    }

    /**
     * Reads an image's declared dimensions from its header without decoding it.
     *
     * This is the check that stops a decompression bomb: a tiny file declaring
     * enormous dimensions is refused here, before anything allocates a pixel buffer.
     */
    private fun guardImageDimensions(source: Path) {
        val stream: ImageInputStream = ImageIO.createImageInputStream(source.toFile())
            ?: throw ImportException(ImportRejection.Damaged("Keeply could not read that image."))
        stream.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                throw ImportException(
                    ImportRejection.Damaged("That image is damaged, or is not a JPG or PNG after all."),
                )
            }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val pixels = width.toLong() * height.toLong()
                if (width > limits.maxImageDimension || height > limits.maxImageDimension ||
                    pixels > limits.maxImagePixels
                ) {
                    throw ImportException(
                        ImportRejection.TooLarge(
                            "That image is ${width}x$height, which is far larger than any photo of a receipt. " +
                                "Keeply will not open it.",
                        ),
                    )
                }
            } catch (e: IOException) {
                throw ImportException(
                    ImportRejection.Damaged("That image is damaged and Keeply could not read its size."),
                )
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Copies the file and fingerprints it in one pass, writing to a temporary name
     * first so an interrupted import never leaves a half-written receipt behind.
     */
    private fun copyAndDigest(source: Path, destination: Path): String {
        Files.createDirectories(destination.parent)
        val temporary = destination.resolveSibling("${destination.fileName}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            Files.newInputStream(source).use { raw ->
                DigestInputStream(raw, digest).use { stream ->
                    copyWithCeiling(stream, temporary)
                }
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temporary) }
            throw ImportException(
                ImportRejection.NoSpace(
                    "Keeply could not save that file. The disk may be full, or the Keeply folder may not be writable.",
                ),
            )
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Copies with a hard ceiling.
     *
     * The size check above used the file's metadata. A file being written while it is
     * imported could still grow past the limit, so the stream itself is bounded.
     */
    private fun copyWithCeiling(stream: InputStream, destination: Path) {
        Files.newOutputStream(destination).use { out ->
            val buffer = ByteArray(COPY_BUFFER)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limits.maxFileBytes) {
                    throw ImportException(
                        ImportRejection.TooLarge("That file grew past Keeply's size limit while it was being read."),
                    )
                }
                out.write(buffer, 0, read)
            }
        }
    }

    private fun describeSize(bytes: Long): String = when {
        bytes >= GIGABYTE -> "%.1f GB".format(bytes.toDouble() / GIGABYTE)
        bytes >= MEGABYTE -> "%.0f MB".format(bytes.toDouble() / MEGABYTE)
        else -> "%.0f KB".format(bytes.toDouble() / KILOBYTE)
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val MAX_NAME_LENGTH = 200
        const val KILOBYTE = 1024.0
        const val MEGABYTE = 1024.0 * 1024
        const val GIGABYTE = 1024.0 * 1024 * 1024
    }
}
