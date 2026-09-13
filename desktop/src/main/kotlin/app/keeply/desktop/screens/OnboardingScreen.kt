package app.keeply.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.keeply.desktop.theme.Spacing

/**
 * First launch.
 *
 * Three lines and a button. Somebody opening a receipt keeper for the first time
 * wants to add a receipt, not read a tour, and the promises here are short enough
 * to be checked against what the application actually does.
 */
@Composable
public fun OnboardingScreen(onAddReceipt: () -> Unit, onTryDemo: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Keep receipts without keeping the clutter.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Spacing.maxProseWidth).semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.large))
        Text(
            text = "Keeply stores your receipts and purchase details privately on this computer.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Spacing.maxProseWidth),
        )
        Spacer(Modifier.height(Spacing.medium))
        Text(
            text = "No account. No cloud.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.section))
        Button(onClick = onAddReceipt) { Text("Add your first receipt") }
        Spacer(Modifier.height(Spacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            TextButton(onClick = onTryDemo) { Text("Show me an example first") }
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    }
}
