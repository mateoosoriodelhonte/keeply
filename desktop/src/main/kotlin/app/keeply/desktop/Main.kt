package app.keeply.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.keeply.desktop.state.KeeplyState
import app.keeply.desktop.theme.KeeplyTheme
import app.keeply.desktop.theme.Spacing
import app.keeply.documents.DataDirectory
import app.keeply.reminders.DesktopNotifier
import app.keeply.services.Keeply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val log = LoggerFactory.getLogger("app.keeply.desktop.Main")

/**
 * Where Keeply keeps its library.
 *
 * The system's own application support folder, unless `keeply.dataDir` says
 * otherwise. The override exists so development and screenshots run against a
 * throwaway folder rather than somebody's real receipts.
 */
private fun chosenDirectory(): DataDirectory = System.getProperty("keeply.dataDir")
    ?.takeIf { it.isNotBlank() }
    ?.let { DataDirectory(java.nio.file.Paths.get(it)) }
    ?: DataDirectory.default()

/** Startup, which can fail in ways a person needs to be told about. */
private sealed interface Startup {
    data object Opening : Startup
    data class Open(val keeply: Keeply) : Startup
    data class Failed(val message: String) : Startup
}

public fun main() {
    // Keeply draws its own interface and never needs a dock icon before the window
    // exists; this keeps the name in the menu bar correct on macOS.
    System.setProperty("apple.awt.application.name", "Keeply")

    application {
        val windowState = rememberWindowState(
            size = DpSize(1_180.dp, 820.dp),
            position = WindowPosition(Alignment.Center),
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Keeply",
        ) {
            KeeplyWindow()
        }
    }
}

@Composable
private fun KeeplyWindow() {
    var startup: Startup by remember { mutableStateOf(Startup.Opening) }
    var reopenFrom: Path? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // Opening reads a database and unpacks the language model on first run, so it
    // happens off the drawing thread with something on screen meanwhile.
    LaunchedEffect(reopenFrom) {
        startup = Startup.Opening
        startup = withContext(Dispatchers.IO) {
            runCatching {
                val directory = reopenFrom?.let(::DataDirectory) ?: chosenDirectory()
                Startup.Open(Keeply.open(directory, DesktopNotifier()))
            }.getOrElse { failure ->
                log.error("Keeply could not start", failure)
                Startup.Failed(
                    when (failure) {
                        is app.keeply.data.KeeplyDatabaseException -> failure.userMessage

                        is app.keeply.ocr.OcrUnavailableException -> failure.userMessage

                        else ->
                            "Keeply could not open its library. Check that you have permission to " +
                                "write to your application support folder, and that the disk is not full."
                    },
                )
            }
        }
    }

    when (val current = startup) {
        Startup.Opening -> KeeplyTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.medium))
                        Text("Opening your library", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        is Startup.Failed -> KeeplyTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().padding(Spacing.page), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Text("Keeply could not start", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = Spacing.maxProseWidth),
                        )
                    }
                }
            }
        }

        is Startup.Open -> {
            val state = remember(current.keeply) { KeeplyState(current.keeply, scope) }

            DisposableEffect(current.keeply) {
                state.start()
                onDispose { runCatching { current.keeply.close() } }
            }

            // Reminders are checked at startup and once a day after that, and only
            // when somebody has switched them on.
            LaunchedEffect(current.keeply, state.reminderPreferences.enabled) {
                if (!state.reminderPreferences.enabled) return@LaunchedEffect
                current.keeply.reminders.start(
                    scope = this,
                    purchases = { current.keeply.store.purchases.all() },
                    preferences = { state.reminderPreferences },
                )
            }

            // A restore replaces the folder underneath the open database, so the
            // application reopens rather than carrying on with a stale handle.
            LaunchedEffect(state.restoredInto) {
                state.restoredInto?.let { root ->
                    scope.launch { reopenFrom = root }
                }
            }

            KeeplyTheme(
                darkTheme = isSystemInDarkTheme(),
                reducedMotion = state.reducedMotion,
                textScale = state.textScale,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KeeplyApp(state)
                }
            }
        }
    }
}
