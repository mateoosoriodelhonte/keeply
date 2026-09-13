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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.state.KeeplyState
import app.keeply.desktop.state.PendingRestore
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.ReminderPreferences

/** Settings, kept to the handful that change how Keeply behaves. */
@Composable
public fun SettingsScreen(
    reminders: ReminderPreferences,
    notificationsAvailable: Boolean,
    reducedMotion: Boolean,
    textScale: Float,
    demoInstalled: Boolean,
    onReminders: (ReminderPreferences) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onTextScale: (Float) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onDemo: () -> Unit,
    pendingRestore: PendingRestore? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text("Reminders", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "Keeply can tell you when a return window is about to close or a warranty is " +
                    "about to end. These are shown by this computer. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.small))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remind me", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = reminders.enabled,
                    onCheckedChange = { onReminders(reminders.copy(enabled = it)) },
                    enabled = notificationsAvailable,
                    modifier = Modifier.semantics { contentDescription = "Show reminders on this computer" },
                )
            }
            if (!notificationsAvailable) {
                Text(
                    text = "This computer does not offer notifications to Keeply. " +
                        "The home screen still shows what needs your attention.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text("Reading and motion", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reduce motion", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = reducedMotion,
                    onCheckedChange = onReducedMotion,
                    modifier = Modifier.semantics { contentDescription = "Reduce animation throughout Keeply" },
                )
            }
            Spacer(Modifier.height(Spacing.small))
            Text("Text size", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = textScale,
                onValueChange = onTextScale,
                valueRange = KeeplyState.MIN_TEXT_SCALE..KeeplyState.MAX_TEXT_SCALE,
                modifier = Modifier.semantics { contentDescription = "Text size throughout Keeply" },
            )
            Text(
                text = "Everything in Keeply gets larger, not just this screen.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text("Your data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "A backup holds your receipts as well as their details, so it restores " +
                    "everything. The spreadsheet and JSON exports hold the details only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Button(onClick = onExportBackup) { Text("Back up Keeply") }
                OutlinedButton(onClick = onImportBackup) { Text("Restore a backup") }
            }
            Spacer(Modifier.height(Spacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                OutlinedButton(onClick = onExportCsv) { Text("Export as a spreadsheet") }
                OutlinedButton(onClick = onExportJson) { Text("Export as JSON") }
            }
        }

        pendingRestore?.let { pending ->
            // Restoring replaces a whole library, so it is never the immediate result
            // of choosing a file. The numbers are here so the choice is an informed one.
            KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
                Text(
                    "Restore this backup?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.small))
                Text(
                    text = "It holds ${pending.purchaseCount} purchases and ${pending.documentCount} files, " +
                        "saved on ${pending.createdAt.take(DATE_LENGTH)}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.small))
                Text(
                    text = if (pending.replacesCount > 0) {
                        "This will replace the ${pending.replacesCount} purchases currently in Keeply. " +
                            "Back them up first if you want to keep them."
                    } else {
                        "Keeply is empty, so nothing will be replaced."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Button(onClick = onConfirmRestore) { Text("Replace and restore") }
                    OutlinedButton(onClick = onCancelRestore) { Text("Cancel") }
                }
            }
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text("Example data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = if (demoInstalled) {
                    "Your library contains example purchases. Removing them leaves anything real untouched."
                } else {
                    "Add a few made-up purchases to see how Keeply works. They are clearly marked and " +
                        "can be removed in one action."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.small))
            OutlinedButton(onClick = onDemo) {
                Text(if (demoInstalled) "Remove example purchases" else "Add example purchases")
            }
        }
    }
}

private const val DATE_LENGTH = 10
