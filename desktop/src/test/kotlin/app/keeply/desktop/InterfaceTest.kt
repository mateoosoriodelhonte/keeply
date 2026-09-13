package app.keeply.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.keeply.desktop.screens.OnboardingScreen
import app.keeply.desktop.screens.ReviewScreen
import app.keeply.desktop.theme.KeeplyTheme
import app.keeply.documents.DataDirectory
import app.keeply.domain.CurrencyCode
import app.keeply.domain.DocumentFormat
import app.keeply.domain.DocumentId
import app.keeply.domain.Field
import app.keeply.domain.FieldSource
import app.keeply.domain.Money
import app.keeply.domain.PurchaseDraft
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.StoredDocument
import app.keeply.extraction.ExtractionNote
import app.keeply.services.ImportOutcome
import app.keeply.services.ReviewedPurchase
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The screens people actually use, driven the way they would drive them.
 *
 * The review screen gets the most attention here, because it is the one place
 * where getting the interface wrong changes what gets saved.
 */
@OptIn(ExperimentalTestApi::class)
class InterfaceTest {

    private val directory = DataDirectory(Paths.get("build/ui-test-data"))

    private fun outcome(
        merchant: Field<String>? = Field.uncertain("8EST 8UY", FieldSource.OCR, evidence = "8EST 8UY"),
        total: Field<Money>? = Field.confident(Money.of("249.99", CurrencyCode.USD), FieldSource.HEURISTIC),
        suggestedReturn: ReturnPolicy = ReturnPolicy.Unknown,
        suggestedSource: ReturnPolicySource = ReturnPolicySource.NONE,
    ) = ImportOutcome(
        document = StoredDocument(
            id = DocumentId.new(),
            kind = app.keeply.domain.DocumentKind.RECEIPT,
            format = DocumentFormat.PNG,
            relativePath = "receipts/ab/missing.png",
            byteSize = 1,
            sha256 = "a".repeat(64),
            importedAt = Instant.parse("2026-09-12T10:00:00Z"),
            originalFileName = "receipt.png",
        ),
        draft = PurchaseDraft(
            sourceDocumentId = null,
            productName = Field.uncertain("Headphones", FieldSource.OCR),
            merchantName = merchant,
            purchaseDate = Field.confident(LocalDate.of(2026, 9, 10), FieldSource.OCR, evidence = "09/10/2026"),
            total = total,
            currency = Field.confident(CurrencyCode.USD, FieldSource.OCR),
            suggestedReturnPolicy = suggestedReturn,
            suggestedReturnSource = suggestedSource,
        ),
        notes = listOf(ExtractionNote("Total", "249.99", "labelled total")),
        text = "8EST 8UY\nTOTAL $249.99",
        textSource = FieldSource.OCR,
        ocr = null,
        preprocessing = null,
        imageAssessment = null,
        duplicates = emptyList(),
        extensionMismatched = false,
    )

    @Test
    fun onboardingMakesItsPromisesInThreeLines() = runComposeUiTest {
        var added = false
        setContent {
            KeeplyTheme { OnboardingScreen(onAddReceipt = { added = true }, onTryDemo = {}, onSkip = {}) }
        }

        onNodeWithText("Keep receipts without keeping the clutter.").assertIsDisplayed()
        onNodeWithText("No account. No cloud.").assertIsDisplayed()
        onNodeWithText("Add your first receipt").performClick()
        assertTrue(added)
    }

    @Test
    fun theReviewScreenSaysWhenKeeplyWasNotSure() = runComposeUiTest {
        setContent {
            KeeplyTheme {
                ReviewScreen(outcome(), directory, onSave = {}, onCancel = {}, onSeeReading = {})
            }
        }

        onNodeWithText("Check what Keeply read").assertIsDisplayed()
        onNodeWithText("Nothing is saved until you say so. Change anything that looks wrong.").assertIsDisplayed()
        // The shop was misread, and the screen shows the text it read rather than
        // presenting the guess as a fact.
        onNodeWithText("Keeply wasn't sure. It read: 8EST 8UY").assertIsDisplayed()
        // Two fields were read cleanly, and both say so.
        assertEquals(2, onAllNodesWithText("Read from the receipt").fetchSemanticsNodes().size)
    }

    @Test
    fun aSavedShopRuleIsLabelledAsTheirsNotTheShopsPolicy() = runComposeUiTest {
        setContent {
            KeeplyTheme {
                ReviewScreen(
                    outcome(
                        suggestedReturn = ReturnPolicy.Days(15),
                        suggestedSource = ReturnPolicySource.SAVED_MERCHANT_RULE,
                    ),
                    directory,
                    onSave = {},
                    onCancel = {},
                    onSeeReading = {},
                )
            }
        }

        onNodeWithText(
            "Using the rule you saved for this shop. It is your rule, not the shop's policy.",
        ).assertIsDisplayed()
    }

    @Test
    fun cancellingSavesNothing() = runComposeUiTest {
        var saved: ReviewedPurchase? = null
        var cancelled = false
        setContent {
            KeeplyTheme {
                ReviewScreen(
                    outcome(),
                    directory,
                    onSave = { saved = it },
                    onCancel = { cancelled = true },
                    onSeeReading = {},
                )
            }
        }

        // The buttons sit below the fields, so the test scrolls the way a person would.
        onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(cancelled)
        assertEquals(null, saved)
    }
}
