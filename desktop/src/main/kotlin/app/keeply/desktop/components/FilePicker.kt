package app.keeply.desktop.components

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The system's own file panel.
 *
 * `FileDialog` rather than Swing's chooser, because on macOS this is the real
 * panel a person recognises, with their sidebar and their recent places in it.
 */
public object FilePicker {

    public fun chooseToOpen(title: String, extensions: Set<String>): Path? = FileDialog(null as Frame?, title, FileDialog.LOAD).run {
        setFilenameFilter { _, name ->
            extensions.isEmpty() || extensions.any { name.lowercase().endsWith(".$it") }
        }
        isVisible = true
        val chosen = file ?: return null
        Paths.get(directory, chosen)
    }

    public fun chooseToSave(title: String, suggestedName: String): Path? = FileDialog(null as Frame?, title, FileDialog.SAVE).run {
        file = suggestedName
        isVisible = true
        val chosen = file ?: return null
        Paths.get(directory, chosen)
    }
}
