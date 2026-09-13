package app.keeply.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseFilter
import app.keeply.domain.ReminderPreferences
import app.keeply.services.ImportOutcome
import app.keeply.services.ImportStage
import app.keeply.services.Insights
import app.keeply.services.Keeply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.LocalDate

/** What Keeply is doing to a file someone dropped on it. */
public data class ImportProgress(val fileName: String, val stage: ImportStage, val index: Int = 1, val total: Int = 1) {
    /** A sentence for the progress area. Says what is happening, not what class is running. */
    public val message: String
        get() = when (stage) {
            ImportStage.Saving -> "Saving your file"

            ImportStage.Preparing -> "Straightening the photo"

            is ImportStage.Reading ->
                if (stage.pages > 1) "Reading page ${stage.page} of ${stage.pages}" else "Reading the text"

            ImportStage.Understanding -> "Working out what it says"

            ImportStage.Finished -> "Ready"
        }
}

/** Something that went wrong, in words a person can act on. */
public data class Problem(val message: String, val detail: String? = null)

/**
 * Everything on screen, and the one place that talks to the services.
 *
 * Screens read from here and call methods on it. Nothing in the interface opens a
 * database, touches a file or blocks a thread, which is what keeps the window
 * responsive while a receipt is being read.
 */
public class KeeplyState(
    public val keeply: Keeply,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val log = LoggerFactory.getLogger(KeeplyState::class.java)

    public var screen: Screen by mutableStateOf(Screen.Home)
        private set
    public var purchases: List<Purchase> by mutableStateOf(emptyList())
        private set
    public var needsAttention: List<Purchase> by mutableStateOf(emptyList())
        private set
    public var insights: Insights? by mutableStateOf(null)
        private set
    public var searchText: String by mutableStateOf("")
        private set
    public var recognisedTerms: List<String> by mutableStateOf(emptyList())
        private set
    public var filter: PurchaseFilter by mutableStateOf(PurchaseFilter())
        private set

    public var importing: ImportProgress? by mutableStateOf(null)
        private set
    public var problem: Problem? by mutableStateOf(null)
        private set
    public var message: String? by mutableStateOf(null)
        private set
    public var onboarding: Boolean by mutableStateOf(false)
        private set

    /** Settings a screen writes directly. There is nothing to coordinate. */
    public var reminderPreferences: ReminderPreferences by mutableStateOf(ReminderPreferences.disabled)
    public var reducedMotion: Boolean by mutableStateOf(false)
    public var textScale: Float by mutableStateOf(1f)

    private var importJob: Job? = null

    /** A backup somebody chose, waiting for them to confirm what restoring will do. */
    public var pendingRestore: PendingRestore? by mutableStateOf(null)
        private set

    /** Set when a restore has replaced the library and the app must reopen it. */
    public var restoredInto: java.nio.file.Path? by mutableStateOf(null)
        private set

    public fun start() {
        onboarding = keeply.store.purchases.count() == 0L
        refresh()
    }

    public fun go(destination: Screen) {
        problem = null
        screen = destination
    }

    public fun back() {
        screen = when (val current = screen) {
            is Screen.Viewer -> current.from
            is Screen.Reading -> current.from
            is Screen.Detail -> Screen.Library
            is Screen.Review -> Screen.Home
            else -> Screen.Home
        }
    }

    public fun dismissProblem() {
        problem = null
    }

    public fun dismissMessage() {
        message = null
    }

    public fun finishOnboarding() {
        onboarding = false
    }

    public fun search(text: String) {
        searchText = text
        refreshLibrary()
    }

    public fun applyFilter(next: PurchaseFilter) {
        filter = next
        refreshLibrary()
    }

    public fun clearFilters() {
        filter = PurchaseFilter()
        searchText = ""
        refreshLibrary()
    }

    public fun refresh() {
        refreshLibrary()
        needsAttention = keeply.library.needsAttention()
        insights = keeply.insights.summarise()
    }

    private fun refreshLibrary() {
        val result = keeply.library.search(searchText, filter)
        purchases = result.purchases
        recognisedTerms = result.recognisedTerms
    }

    /**
     * Reads a dropped file.
     *
     * Cancelling replaces the job rather than queueing another, so dropping a
     * second receipt while the first is being read does the obvious thing.
     */
    public fun import(file: Path) {
        importJob?.cancel()
        importJob = scope.launch {
            problem = null
            importing = ImportProgress(file.fileName.toString(), ImportStage.Saving)
            try {
                val outcome = withContext(Dispatchers.IO) {
                    keeply.imports.import(file) { stage ->
                        importing = importing?.copy(stage = stage)
                    }
                }
                importing = null
                screen = Screen.Review(outcome)
            } catch (e: app.keeply.documents.ImportException) {
                importing = null
                problem = Problem(e.rejection.message)
            } catch (e: app.keeply.ocr.OcrUnavailableException) {
                importing = null
                problem = Problem(e.userMessage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                importing = null
                throw e
            } catch (e: Exception) {
                log.warn("Import failed", e)
                importing = null
                problem = Problem(
                    "Keeply could not read that file.",
                    "You can still add the purchase yourself, and the file has been kept.",
                )
            }
        }
    }

    public fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importing = null
    }

    public fun installDemo() {
        scope.launch {
            withContext(Dispatchers.IO) { keeply.demo.install(today()) }
            onboarding = false
            refresh()
            message = "Added a few example purchases so you can see how Keeply works."
        }
    }

    public fun clearDemo() {
        scope.launch {
            withContext(Dispatchers.IO) { keeply.demo.clear() }
            refresh()
            message = "Example purchases removed."
        }
    }

    public fun scaleTextTo(value: Float) {
        textScale = value.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
    }

    /**
     * Looks at a backup and describes it, without touching anything.
     *
     * Restoring replaces a person's whole library, so it is never the immediate
     * result of choosing a file. They are told exactly what the archive holds, and
     * what will happen, and asked.
     */
    public fun proposeRestore(archive: Path) {
        problem = null
        runCatching { keeply.backups.inspect(archive) }
            .onSuccess { summary ->
                if (!summary.restorable) {
                    problem = Problem(summary.problem ?: "Keeply cannot restore that backup.")
                    return
                }
                pendingRestore = PendingRestore(
                    archive = archive,
                    purchaseCount = summary.manifest.purchaseCount,
                    documentCount = summary.manifest.documentCount,
                    createdAt = summary.manifest.createdAt,
                    replacesCount = keeply.store.purchases.count(),
                )
            }
            .onFailure {
                problem = Problem(
                    (it as? app.keeply.backup.BackupException)?.userMessage
                        ?: "That file is not a Keeply backup.",
                )
            }
    }

    public fun cancelRestore() {
        pendingRestore = null
    }

    /**
     * Restores, having been told to.
     *
     * The database is closed first, because replacing the folder underneath an open
     * SQLite connection is how a library gets damaged. The application then reopens
     * from the restored folder.
     */
    public fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        scope.launch {
            try {
                val directory = keeply.directory
                withContext(Dispatchers.IO) {
                    keeply.close()
                    app.keeply.backup.BackupReader().restore(
                        pending.archive,
                        directory,
                        app.keeply.backup.RestoreMode.REPLACE_EXISTING,
                    )
                }
                restoredInto = directory.root
            } catch (e: app.keeply.backup.BackupException) {
                problem = Problem(e.userMessage)
            } catch (e: Exception) {
                log.warn("Restore failed", e)
                problem = Problem("Keeply could not restore that backup. Your library has not been changed.")
            }
        }
    }

    public fun report(problem: Problem) {
        this.problem = problem
    }

    public fun announce(text: String) {
        message = text
    }

    public fun todayIs(): LocalDate = today()

    public companion object {
        public const val MIN_TEXT_SCALE: Float = 0.9f
        public const val MAX_TEXT_SCALE: Float = 1.6f
    }
}

/** A backup waiting for confirmation, and what restoring it would replace. */
public data class PendingRestore(
    val archive: Path,
    val purchaseCount: Long,
    val documentCount: Long,
    val createdAt: String,
    val replacesCount: Long,
)

/** The outcome a review screen is working on, or null when there is not one. */
public val KeeplyState.reviewing: ImportOutcome?
    get() = (screen as? Screen.Review)?.outcome
