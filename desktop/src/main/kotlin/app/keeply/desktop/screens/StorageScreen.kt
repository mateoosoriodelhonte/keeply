package app.keeply.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.theme.Spacing
import app.keeply.services.StorageReport

/**
 * What Keeply is using, and what can go.
 *
 * Only the rebuildable lines get a button. Original receipts, manuals and photos
 * have no delete path from this screen at all: removing one of those is a
 * deliberate act on that purchase, not a tidy-up.
 */
@Composable
public fun StorageScreen(report: StorageReport, onClearRebuildable: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = "Storage",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Keeply keeps everything in one folder on this computer. " +
                "Nothing here is stored anywhere else.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = Spacing.maxProseWidth),
        )

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            report.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(line.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (line.rebuildable) {
                                "Keeply can rebuild this"
                            } else {
                                if (line.fileCount == 1L) "1 file" else "${line.fileCount} files"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(describeSize(line.bytes), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Spacing.small))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleLarge)
                Text(describeSize(report.totalBytes), style = MaterialTheme.typography.titleLarge)
            }
        }

        if (report.rebuildableBytes > 0) {
            KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
                Text(
                    "Free up space",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.small))
                Text(
                    text = "Thumbnails and straightened copies of your photos take up " +
                        "${describeSize(report.rebuildableBytes)}. Keeply makes these from your original " +
                        "files and can make them again, so deleting them loses nothing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.small))
                OutlinedButton(onClick = onClearRebuildable) { Text("Clear rebuildable files") }
            }
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text(
                "Your receipts are never cleared from here",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "Receipts, manuals and photos are the things Keeply cannot get back. " +
                    "Removing one of those is done on the purchase itself, and Keeply asks first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sizes people recognise. Nobody wants to read a receipt folder in bytes. */
internal fun describeSize(bytes: Long): String = when {
    bytes >= GIGABYTE -> "%.1f GB".format(bytes.toDouble() / GIGABYTE)
    bytes >= MEGABYTE -> "%.0f MB".format(bytes.toDouble() / MEGABYTE)
    bytes >= KILOBYTE -> "%.0f KB".format(bytes.toDouble() / KILOBYTE)
    bytes == 0L -> "Empty"
    else -> "$bytes bytes"
}

private const val KILOBYTE = 1024.0
private const val MEGABYTE = 1024.0 * 1024
private const val GIGABYTE = 1024.0 * 1024 * 1024
