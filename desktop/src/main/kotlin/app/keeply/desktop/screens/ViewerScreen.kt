package app.keeply.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.keeply.desktop.components.DocumentImage
import app.keeply.desktop.components.rememberDocumentImage
import app.keeply.desktop.theme.Spacing
import app.keeply.documents.DataDirectory
import app.keeply.domain.StoredDocument

/**
 * Looking at a saved document.
 *
 * Reading only. The file on disk is never written to, and nothing Keeply derives
 * from it is stored here, so opening a receipt cannot change it.
 */
@Composable
public fun ViewerScreen(document: StoredDocument?, directory: DataDirectory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember(document?.id) { mutableStateOf(0) }
    val image = rememberDocumentImage(directory, document, page)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                text = document?.originalFileName ?: "Document",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pages = (image as? DocumentImage.Ready)?.pageCount ?: 1
            if (pages > 1) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Previous") }
                Text(
                    "Page ${page + 1} of $pages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { if (page < pages - 1) page++ }, enabled = page < pages - 1) { Text("Next") }
            }
        }

        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.medium),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (image) {
                DocumentImage.Loading -> CircularProgressIndicator()

                is DocumentImage.Failed -> Text(image.message, style = MaterialTheme.typography.bodyLarge)

                is DocumentImage.Ready -> Image(
                    bitmap = image.bitmap,
                    contentDescription = document?.originalFileName ?: "The saved document",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
