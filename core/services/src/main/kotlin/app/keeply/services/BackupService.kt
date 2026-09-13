package app.keeply.services

import app.keeply.backup.BackupManifest
import app.keeply.backup.BackupProgress
import app.keeply.backup.BackupReader
import app.keeply.backup.BackupSummary
import app.keeply.backup.BackupWriter
import app.keeply.backup.PurchaseExport
import app.keeply.backup.RestoreMode
import app.keeply.backup.RestoreProgress
import app.keeply.data.KeeplyStore
import app.keeply.documents.DataDirectory
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate

/** Backups and exports, in the terms the screens use. */
public class BackupService(
    private val store: KeeplyStore,
    private val directory: DataDirectory,
    private val writer: BackupWriter,
    private val reader: BackupReader,
    private val appVersion: String,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    public suspend fun export(target: Path, onProgress: (BackupProgress) -> Unit = {}): BackupManifest =
        writer.write(store, target, onProgress)

    public fun inspect(archive: Path): BackupSummary = reader.inspect(archive)

    public suspend fun restore(
        archive: Path,
        mode: RestoreMode = RestoreMode.ONLY_IF_EMPTY,
        onProgress: (RestoreProgress) -> Unit = {},
    ): BackupManifest = reader.restore(archive, directory, mode, onProgress)

    /** Every purchase as a spreadsheet, including archived ones. */
    public fun exportCsv(today: LocalDate = LocalDate.now(clock)): String = PurchaseExport.toCsv(rows(today))

    public fun exportJson(today: LocalDate = LocalDate.now(clock)): String = PurchaseExport.toJson(rows(today), appVersion)

    private fun rows(today: LocalDate) = PurchaseExport.toRows(
        purchases = everything(),
        categories = store.categories.all(),
        today = today,
    )

    /** Archived purchases are part of a person's record, so an export includes them. */
    private fun everything() = store.purchases.byIds(store.purchases.fingerprints().mapNotNull { it.purchaseId })
}
