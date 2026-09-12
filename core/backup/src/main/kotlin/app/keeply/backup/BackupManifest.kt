package app.keeply.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One file inside a backup archive. */
@Serializable
public data class BackupEntry(
    /** Path inside the archive. Always relative, always forward slashes. */
    val path: String,
    val byteSize: Long,
    val sha256: String,
)

/**
 * What a backup archive says about itself.
 *
 * Read and checked before a single file is extracted. An archive whose manifest
 * does not parse, or claims a version Keeply does not understand, is refused
 * rather than partially restored.
 */
@Serializable
public data class BackupManifest(
    @SerialName("format_version")
    val formatVersion: Int,
    @SerialName("schema_version")
    val schemaVersion: Long,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("purchase_count")
    val purchaseCount: Long,
    @SerialName("document_count")
    val documentCount: Long,
    val entries: List<BackupEntry>,
) {
    public companion object {
        /** Bumped only when the archive layout changes in a way older Keeply cannot read. */
        public const val CURRENT_FORMAT_VERSION: Int = 1
        public const val MANIFEST_PATH: String = "manifest.json"
        public const val DATABASE_PATH: String = "database/keeply.db"
        public const val FILES_PREFIX: String = "files/"
    }
}

/** What an archive contains, shown before anything is restored. */
public data class BackupSummary(
    val manifest: BackupManifest,
    val totalBytes: Long,
    /** True when this build of Keeply can restore it. */
    val restorable: Boolean,
    val problem: String?,
)

/** Why an archive was refused, in words meant for the screen. */
public class BackupException(public val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)
