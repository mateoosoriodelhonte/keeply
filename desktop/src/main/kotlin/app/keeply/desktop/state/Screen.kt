package app.keeply.desktop.state

import app.keeply.domain.DocumentId
import app.keeply.domain.PurchaseId
import app.keeply.services.ImportOutcome

/** Where in Keeply someone is. */
public sealed interface Screen {
    public val title: String

    public data object Home : Screen {
        override val title: String = "Home"
    }

    public data object Library : Screen {
        override val title: String = "All purchases"
    }

    public data class Detail(val id: PurchaseId) : Screen {
        override val title: String = "Purchase"
    }

    /** What Keeply read, waiting to be checked. */
    public data class Review(val outcome: ImportOutcome) : Screen {
        override val title: String = "Check what Keeply read"
    }

    public data class Viewer(val documentId: DocumentId, val from: Screen) : Screen {
        override val title: String = "Document"
    }

    /** The raw text and the rules behind it. Off the everyday path. */
    public data class Reading(val outcome: ImportOutcome, val from: Screen) : Screen {
        override val title: String = "What Keeply read"
    }

    public data object Storage : Screen {
        override val title: String = "Storage"
    }

    public data object Privacy : Screen {
        override val title: String = "Your data stays yours"
    }

    public data object Settings : Screen {
        override val title: String = "Settings"
    }
}
