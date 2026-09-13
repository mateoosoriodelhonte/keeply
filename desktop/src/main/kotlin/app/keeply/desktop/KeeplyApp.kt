package app.keeply.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.keeply.data.AttachmentRole
import app.keeply.desktop.components.FilePicker
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.components.receiveDroppedFiles
import app.keeply.desktop.screens.DetailScreen
import app.keeply.desktop.screens.HomeScreen
import app.keeply.desktop.screens.LibraryScreen
import app.keeply.desktop.screens.OnboardingScreen
import app.keeply.desktop.screens.PrivacyScreen
import app.keeply.desktop.screens.ReadingScreen
import app.keeply.desktop.screens.ReviewScreen
import app.keeply.desktop.screens.SettingsScreen
import app.keeply.desktop.screens.StorageScreen
import app.keeply.desktop.screens.ViewerScreen
import app.keeply.desktop.state.KeeplyState
import app.keeply.desktop.state.Screen
import app.keeply.desktop.theme.Spacing
import app.keeply.services.StorageReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * The window's contents.
 *
 * Navigation is a handful of screens and a rail, because Keeply has a handful of
 * things to do and hiding them behind menus would make a small application feel
 * like a large one.
 */
@Composable
public fun KeeplyApp(
    state: KeeplyState,
    modifier: Modifier = Modifier,
    /** Whether this desktop can show notifications at all. Settings needs the truth. */
    notificationsAvailable: Boolean = false,
) {
    val searchFocus = remember { FocusRequester() }
    // Measuring storage walks the data folder, so it happens off the drawing
    // thread and only when the storage screen is actually open.
    var storageReport by remember { mutableStateOf(StorageReport(emptyList(), 0)) }

    LaunchedEffect(state.screen, state.purchases.size) {
        if (state.screen is Screen.Storage) {
            storageReport = withContext(Dispatchers.IO) { state.keeply.storage.report() }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .receiveDroppedFiles { files -> files.firstOrNull()?.let(state::import) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    // Cmd is the right modifier on macOS, which is Keeply's first platform.
                    event.isMetaPressed && event.key == Key.O -> {
                        addReceipt(state)
                        true
                    }

                    event.isMetaPressed && event.key == Key.F -> {
                        state.go(Screen.Library)
                        runCatching { searchFocus.requestFocus() }
                        true
                    }

                    event.key == Key.Escape -> {
                        state.back()
                        true
                    }

                    else -> false
                }
            },
    ) {
        if (state.onboarding) {
            OnboardingScreen(
                onAddReceipt = {
                    state.finishOnboarding()
                    addReceipt(state)
                },
                onTryDemo = state::installDemo,
                onSkip = state::finishOnboarding,
            )
            return@Box
        }

        Row(Modifier.fillMaxSize()) {
            Rail(state)
            Column(Modifier.fillMaxSize()) {
                Banners(state)
                Box(Modifier.fillMaxSize().widthIn(max = Spacing.maxContentWidth)) {
                    Content(state, storageReport, notificationsAvailable, searchFocus)
                }
            }
        }
    }
}

@Composable
private fun Rail(state: KeeplyState) {
    val current = state.screen
    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
        RailItem("Home", current is Screen.Home) { state.go(Screen.Home) }
        RailItem("Purchases", current is Screen.Library || current is Screen.Detail) { state.go(Screen.Library) }
        RailItem("Storage", current is Screen.Storage) { state.go(Screen.Storage) }
        RailItem("Privacy", current is Screen.Privacy) { state.go(Screen.Privacy) }
        RailItem("Settings", current is Screen.Settings) { state.go(Screen.Settings) }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        // Text rather than an icon: five words a person can read beats five
        // pictograms they have to learn.
        icon = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/**
 * Progress, problems and confirmations.
 *
 * Announced as a live region, so a screen reader says what happened rather than
 * leaving somebody to discover that a banner appeared.
 */
@Composable
private fun Banners(state: KeeplyState) {
    val importing = state.importing
    AnimatedVisibility(visible = importing != null) {
        importing?.let { progress ->
            KeeplyCard(Modifier.fillMaxWidth().padding(Spacing.medium)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(Spacing.large))
                    Spacer(Modifier.widthIn(min = Spacing.medium))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            text = progress.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = state::cancelImport) { Text("Stop") }
                }
            }
        }
    }

    state.problem?.let { problem ->
        KeeplyCard(Modifier.fillMaxWidth().padding(Spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = problem.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    problem.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = state::dismissProblem) { Text("Dismiss") }
            }
        }
    }

    state.message?.let { message ->
        KeeplyCard(Modifier.fillMaxWidth().padding(Spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = state::dismissMessage) { Text("OK") }
            }
        }
    }
}

/** One purchase and its documents, loaded together. */
private data class LoadedPurchase(
    val purchase: app.keeply.domain.Purchase,
    val receipt: app.keeply.domain.StoredDocument?,
    val receiptText: app.keeply.domain.DocumentText?,
    val manuals: List<app.keeply.domain.StoredDocument>,
)

@Composable
private fun Content(state: KeeplyState, storageReport: StorageReport, notificationsAvailable: Boolean, searchFocus: FocusRequester) {
    val keeply = state.keeply
    when (val screen = state.screen) {
        Screen.Home -> HomeScreen(
            needsAttention = state.needsAttention,
            recent = state.recent,
            insights = state.insights,
            today = state.todayIs(),
            onOpen = { state.go(Screen.Detail(it.id)) },
            onAddReceipt = { addReceipt(state) },
            onSeeAll = { state.go(Screen.Library) },
            onTryDemo = state::installDemo,
        )

        Screen.Library -> LibraryScreen(
            purchases = state.purchases,
            searchText = state.searchText,
            recognisedTerms = state.recognisedTerms,
            filter = state.filter,
            today = state.todayIs(),
            onSearch = state::search,
            onFilter = state::applyFilter,
            onClear = state::clearFilters,
            onOpen = { state.go(Screen.Detail(it.id)) },
            modifier = Modifier.focusRequester(searchFocus),
        )

        is Screen.Detail -> {
            // Loaded once per purchase rather than on every recomposition, and the
            // documents with it: a detail view is five queries, not five per frame.
            val loaded = remember(screen.id, state.purchases) {
                keeply.store.purchases.get(screen.id)?.let { purchase ->
                    val receipt = purchase.receiptDocumentId?.let { keeply.store.documents.get(it) }
                    LoadedPurchase(
                        purchase = purchase,
                        receipt = receipt,
                        receiptText = receipt?.let { keeply.store.documents.text(it.id) },
                        manuals = keeply.store.purchases.attachments(purchase.id)[AttachmentRole.MANUAL]
                            .orEmpty()
                            .mapNotNull { keeply.store.documents.get(it) },
                    )
                }
            }

            if (loaded == null) {
                // Navigating is a side effect, so it happens after composition
                // rather than during it.
                LaunchedEffect(screen.id) { state.go(Screen.Library) }
            } else {
                val purchase = loaded.purchase
                DetailScreen(
                    purchase = purchase,
                    receipt = loaded.receipt,
                    receiptText = loaded.receiptText,
                    manuals = loaded.manuals,
                    directory = keeply.directory,
                    today = state.todayIs(),
                    onOpenDocument = { state.go(Screen.Viewer(it.id, screen)) },
                    onArchive = {
                        keeply.purchases.archive(purchase.id, !purchase.isArchived)
                        state.refresh()
                        state.go(Screen.Library)
                    },
                    onBack = state::back,
                )
            }
        }

        is Screen.Review -> ReviewScreen(
            outcome = screen.outcome,
            directory = keeply.directory,
            onSave = { reviewed ->
                val saved = keeply.purchases.save(reviewed, receiptText = screen.outcome.text)
                state.refresh()
                state.announce("Saved ${saved.productName}.")
                state.go(Screen.Detail(saved.id))
            },
            onCancel = {
                state.announce("Nothing was saved. The file you imported is still in Keeply's folder.")
                state.go(Screen.Home)
            },
            onSeeReading = { state.go(Screen.Reading(screen.outcome, screen)) },
        )

        is Screen.Reading -> ReadingScreen(outcome = screen.outcome, onBack = state::back)

        is Screen.Viewer -> ViewerScreen(
            document = keeply.store.documents.get(screen.documentId),
            directory = keeply.directory,
            onBack = state::back,
        )

        Screen.Storage -> StorageScreen(
            report = storageReport,
            onClearRebuildable = {
                val freed = keeply.storage.clearRebuildable()
                state.announce("Freed ${app.keeply.desktop.screens.describeSize(freed)}.")
            },
        )

        Screen.Privacy -> PrivacyScreen(dataDirectory = keeply.directory.root)

        Screen.Settings -> SettingsScreen(
            reminders = state.reminderPreferences,
            notificationsAvailable = notificationsAvailable,
            reducedMotion = state.reducedMotion,
            textScale = state.textScale,
            demoInstalled = state.demoInstalled,
            onReminders = { state.reminderPreferences = it },
            onReducedMotion = { state.reducedMotion = it },
            onTextScale = state::scaleTextTo,
            onExportBackup = { exportBackup(state) },
            onImportBackup = { importBackup(state) },
            pendingRestore = state.pendingRestore,
            onConfirmRestore = state::confirmRestore,
            onCancelRestore = state::cancelRestore,
            onExportCsv = { exportText(state, "keeply-purchases.csv") { keeply.backups.exportCsv() } },
            onExportJson = { exportText(state, "keeply-purchases.json") { keeply.backups.exportJson() } },
            onDemo = { if (state.demoInstalled) state.clearDemo() else state.installDemo() },
        )
    }
}

private fun addReceipt(state: KeeplyState) {
    FilePicker.chooseToOpen("Choose a receipt", setOf("jpg", "jpeg", "png", "pdf"))?.let(state::import)
}

private fun exportBackup(state: KeeplyState) {
    val target: Path = FilePicker.chooseToSave("Back up Keeply", "keeply-backup.keeplybackup") ?: return
    state.keeply.let { keeply ->
        kotlinx.coroutines.MainScope().launch {
            runCatching { withContext(Dispatchers.IO) { keeply.backups.export(target) } }
                .onSuccess { state.announce("Backed up ${it.purchaseCount} purchases to ${target.fileName}.") }
                .onFailure {
                    state.report(
                        app.keeply.desktop.state.Problem(
                            (it as? app.keeply.backup.BackupException)?.userMessage
                                ?: "Keeply could not write that backup.",
                        ),
                    )
                }
        }
    }
}

private fun importBackup(state: KeeplyState) {
    val archive = FilePicker.chooseToOpen("Restore a backup", setOf("keeplybackup", "zip")) ?: return
    state.proposeRestore(archive)
}

private fun exportText(state: KeeplyState, suggestedName: String, produce: () -> String) {
    val target = FilePicker.chooseToSave("Export", suggestedName) ?: return
    runCatching { java.nio.file.Files.writeString(target, produce()) }
        .onSuccess { state.announce("Exported to ${target.fileName}.") }
        .onFailure { state.report(app.keeply.desktop.state.Problem("Keeply could not write that file.")) }
}
