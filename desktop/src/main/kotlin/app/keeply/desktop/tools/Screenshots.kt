package app.keeply.desktop.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.keeply.desktop.KeeplyApp
import app.keeply.desktop.state.KeeplyState
import app.keeply.desktop.state.Screen
import app.keeply.desktop.theme.KeeplyTheme
import app.keeply.documents.DataDirectory
import app.keeply.reminders.Notifier
import app.keeply.services.Keeply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

/**
 * Renders Keeply's screens to PNG files without opening a window.
 *
 * These are the images in the documentation. Generating them from the real
 * composables against demo data means they cannot drift from the application the
 * way hand-taken screenshots do, and it means they can be regenerated on a machine
 * with no display at all.
 *
 * Demo data only. No real receipt has ever been near this.
 */
@OptIn(ExperimentalComposeUiApi::class)
public object Screenshots {

    /**
     * Logical size, then the device pixels that produces.
     *
     * Rendering at two device pixels per logical pixel gives a crisp image at the
     * size somebody actually sees, rather than one where everything is twice as
     * large as it is in the application.
     */
    private const val LOGICAL_WIDTH = 1_280
    private const val LOGICAL_HEIGHT = 860
    private const val SCALE = 2f
    private const val WARMUP_FRAMES = 6
    private const val FRAME_PAUSE_MILLIS = 250L

    public fun writeTo(directory: Path, library: Path): List<Path> {
        Files.createDirectories(directory)
        library.toFile().deleteRecursively()

        val keeply = Keeply.open(DataDirectory(library), Notifier.disabled)
        return try {
            keeply.demo.install(LocalDate.now())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val state = KeeplyState(keeply, scope)
            state.start()
            state.finishOnboarding()

            buildList {
                add(render(directory.resolve("home.png")) { KeeplyApp(state) })

                state.go(Screen.Library)
                add(render(directory.resolve("library.png")) { KeeplyApp(state) })

                keeply.store.purchases.all().firstOrNull()?.let { purchase ->
                    state.go(Screen.Detail(purchase.id))
                    add(render(directory.resolve("purchase.png")) { KeeplyApp(state) })
                }

                state.go(Screen.Storage)
                add(render(directory.resolve("storage.png")) { KeeplyApp(state) })

                state.go(Screen.Privacy)
                add(render(directory.resolve("privacy.png")) { KeeplyApp(state) })

                state.go(Screen.Settings)
                add(render(directory.resolve("settings.png")) { KeeplyApp(state) })
            }
        } finally {
            keeply.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(target: Path, dark: Boolean = false, content: @Composable () -> Unit): Path {
        ImageComposeScene(
            width = (LOGICAL_WIDTH * SCALE).toInt(),
            height = (LOGICAL_HEIGHT * SCALE).toInt(),
            density = Density(SCALE),
        ) {
            KeeplyTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }.let { scene ->
            try {
                // Receipts load from disk on a background thread. Rendering a few
                // frames apart lets that finish, so a screenshot shows the receipt
                // rather than a spinner.
                repeat(WARMUP_FRAMES) {
                    scene.render()
                    Thread.sleep(FRAME_PAUSE_MILLIS)
                }
                val image = scene.render()
                val data = image.encodeToData(EncodedImageFormat.PNG)
                    ?: error("Could not encode ${target.fileName}")
                Files.write(target, data.bytes)
            } finally {
                scene.close()
            }
        }
        return target
    }
}

public fun main(args: Array<String>) {
    val target = Paths.get(args.getOrElse(0) { "build/screenshots" })
    val library = Paths.get(args.getOrElse(1) { "build/screenshot-library" })
    val written = Screenshots.writeTo(target, library)
    println("Wrote ${written.size} screenshots to ${target.toAbsolutePath()}")
}
