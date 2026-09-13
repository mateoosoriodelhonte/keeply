package app.keeply.desktop.components

import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path

/**
 * Accepts receipts dropped anywhere on the window.
 *
 * Dropping a file on an application is the fastest way to get it in, and it is
 * how most people will use Keeply once they have used it once. The whole window
 * is the target rather than a small rectangle, and it shows a border while
 * something is over it so the drop is obviously going to land.
 *
 * Nothing about the dropped file is trusted here: this only reports a path, and
 * the import pipeline decides whether it is something Keeply will take.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun Modifier.receiveDroppedFiles(onFiles: (List<Path>) -> Unit): Modifier {
    var active by remember { mutableStateOf(false) }
    val highlight = MaterialTheme.colorScheme.primary

    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                active = true
            }

            override fun onExited(event: DragAndDropEvent) {
                active = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                active = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                active = false
                val paths = runCatching {
                    val transferable = event.awtTransferable
                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return@runCatching emptyList()
                    @Suppress("UNCHECKED_CAST")
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
                        .map(File::toPath)
                }.getOrDefault(emptyList())

                if (paths.isEmpty()) return false
                onFiles(paths)
                return true
            }
        }
    }

    return this
        .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
        .then(if (active) Modifier.border(2.dp, highlight, MaterialTheme.shapes.medium) else Modifier)
}
