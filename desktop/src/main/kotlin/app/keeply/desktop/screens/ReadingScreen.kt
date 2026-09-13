package app.keeply.desktop.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import app.keeply.desktop.components.KeeplyCard
import app.keeply.desktop.theme.Spacing
import app.keeply.domain.FieldSource
import app.keeply.services.ImportOutcome

/**
 * The raw reading and the rules behind it.
 *
 * Off the everyday path: somebody saving a receipt never has to come here. It
 * exists because "Keeply got that wrong" is a much easier conversation when you
 * can see the text it read and which rule produced each field, and because an
 * extraction pipeline nobody can inspect is one nobody can trust.
 */
@Composable
public fun ReadingScreen(outcome: ImportOutcome, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.page),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            text = "What Keeply read",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        KeeplyCard {
            Text("How it was read", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            Fact(
                "Source",
                when (outcome.textSource) {
                    FieldSource.PDF_TEXT -> "The PDF's own text layer, so no text recognition was needed"
                    else -> "Text recognition on the image"
                },
            )
            outcome.ocr?.let { ocr ->
                Fact("Engine", ocr.engine)
                Fact("Average confidence", "${ocr.meanConfidence.toInt()}%")
                Fact("Time taken", "${ocr.durationMillis} ms")
                Fact("Words read", ocr.words.size.toString())
                val unsure = ocr.lowConfidenceWords()
                if (unsure.isNotEmpty()) {
                    Fact(
                        "Words it was unsure of",
                        unsure.take(UNSURE_LIMIT).joinToString(", ") { "${it.text} (${it.confidence.toInt()}%)" },
                    )
                }
            }
            outcome.preprocessing?.let { prepared ->
                Fact("Image steps", prepared.steps.joinToString(", ") { it.name.lowercase().replace('_', ' ') })
                Fact("Page found in the photo", if (prepared.documentFound) "Yes, and straightened" else "No, used the whole image")
            }
            outcome.imageAssessment?.let { assessment ->
                Fact("Sharpness", assessment.sharpness.toInt().toString())
                Fact("Brightness", assessment.brightness.toInt().toString())
            }
            if (outcome.extensionMismatched) {
                Fact("Note", "The file's extension did not match its contents. Keeply used the contents.")
            }
        }

        KeeplyCard {
            Text(
                "What each field came from",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.small))
            outcome.notes.forEach { note ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.tight)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(note.field, style = MaterialTheme.typography.titleMedium)
                        Text(
                            note.outcome,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        note.rule,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    note.evidence?.let { evidence ->
                        Text(
                            text = evidence,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        KeeplyCard {
            Text("The text itself", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.small))
            // Monospaced and horizontally scrollable, because the column alignment of
            // a till receipt is most of what makes it readable.
            Text(
                text = outcome.text.ifBlank { "Keeply could not read any text from this file." },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.tight),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private const val UNSURE_LIMIT = 12
