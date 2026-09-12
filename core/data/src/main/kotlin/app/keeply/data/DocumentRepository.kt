package app.keeply.data

import app.keeply.data.sql.KeeplyDatabase
import app.keeply.domain.DocumentId
import app.keeply.domain.DocumentKind
import app.keeply.domain.DocumentText
import app.keeply.domain.StoredDocument

/** How much space one kind of file is using. Shown on the storage screen. */
public data class StorageUsage(val kind: DocumentKind, val fileCount: Long, val totalBytes: Long)

/**
 * Records of the files Keeply holds, and of any text derived from them.
 *
 * The two are deliberately separate tables: re-reading a receipt replaces the text
 * and never touches the record of the original.
 */
public class DocumentRepository internal constructor(private val database: KeeplyDatabase) {
    private val queries get() = database.documentQueries

    public fun get(id: DocumentId): StoredDocument? = queries.selectById(id.value).executeAsOneOrNull()?.let(Mappers::toDocument)

    public fun findBySha256(sha256: String): StoredDocument? = queries.selectBySha256(sha256).executeAsOneOrNull()?.let(Mappers::toDocument)

    public fun all(): List<StoredDocument> = queries.selectAll().executeAsList().map(Mappers::toDocument)

    public fun byKind(kind: DocumentKind): List<StoredDocument> = queries.selectByKind(kind.name).executeAsList().map(Mappers::toDocument)

    public fun insert(document: StoredDocument) {
        queries.insert(
            id = document.id.value,
            kind = document.kind.name,
            format = document.format.name,
            relative_path = document.relativePath,
            byte_size = document.byteSize,
            sha256 = document.sha256,
            imported_at = document.importedAt.toEpochMilli(),
            original_file_name = document.originalFileName,
            page_count = document.pageCount?.toLong(),
        )
    }

    public fun delete(id: DocumentId) {
        queries.deleteById(id.value)
    }

    public fun count(): Long = queries.countAll().executeAsOne()

    public fun usage(): List<StorageUsage> = queries.totalBytesByKind().executeAsList().mapNotNull { row ->
        DocumentKind.entries.firstOrNull { it.name == row.kind }?.let {
            StorageUsage(it, row.file_count, row.total_bytes ?: 0L)
        }
    }

    // --- Derived text --------------------------------------------------------

    public fun text(id: DocumentId): DocumentText? = queries.textFor(id.value).executeAsOneOrNull()?.let(Mappers::toDocumentText)

    public fun saveText(text: DocumentText) {
        queries.upsertText(
            document_id = text.documentId.value,
            text = text.text,
            source = text.source.name,
            mean_confidence = text.meanConfidence,
            extracted_at = text.extractedAt.toEpochMilli(),
            engine = text.engine,
            duration_millis = text.durationMillis,
        )
    }

    public fun deleteText(id: DocumentId) {
        queries.deleteText(id.value)
    }
}
