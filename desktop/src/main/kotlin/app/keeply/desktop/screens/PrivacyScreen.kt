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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.theme.Spacing
import java.nio.file.Path

/**
 * What Keeply does with a person's data, in words they can check.
 *
 * Every line here corresponds to something enforced in the build or the code: the
 * privacy guard fails the build if anything outside the optional local-AI module
 * gains network access, and the folder shown is the only place Keeply writes.
 */
@Composable
public fun PrivacyScreen(dataDirectory: Path, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = "Your data stays yours",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Receipts can say where you live, what you buy and how you pay. " +
                "Keeply is built so that none of that has to go anywhere.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = Spacing.maxProseWidth),
        )

        Spacer(Modifier.height(Spacing.small))
        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            PromiseList(
                title = "What Keeply does",
                items = listOf(
                    "Stores your receipts as files on this computer, in one folder.",
                    "Reads receipts here, using text recognition built into the app.",
                    "Works completely without an internet connection.",
                    "Lets you export everything as a spreadsheet or a backup, whenever you want.",
                ),
            )
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            PromiseList(
                title = "What Keeply does not do",
                items = listOf(
                    "No account, and no sign-in.",
                    "No cloud storage, and no syncing.",
                    "No analytics, no tracking, and no advertising.",
                    "No sending your receipts anywhere to be read.",
                    "No automatic uploads of any kind.",
                ),
            )
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text(
                text = "Optional help from a local model",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "If you choose to run Ollama on this computer, Keeply can ask it to tidy up an " +
                    "abbreviated product name or suggest a category. It is switched off unless you turn " +
                    "it on, Keeply only ever talks to your own machine, and it never installs or " +
                    "downloads anything for you. Everything works without it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        KeeplyCard(Modifier.widthIn(max = Spacing.maxProseWidth)) {
            Text(
                text = "Where your files are",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = dataDirectory.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = "Keeply never writes outside this folder. You can copy it, back it up, or open " +
                    "it yourself. Deleting it deletes your Keeply library, so take a backup first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromiseList(title: String, items: List<String>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.small))
    items.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.tight)) {
            Text(
                text = "·",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.widthIn(min = Spacing.small))
            Text(
                text = item,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
