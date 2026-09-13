package app.keeply.reminders

import app.keeply.domain.Reminder

/**
 * Shows a reminder on the desktop.
 *
 * Everything here happens on the machine. There is no push service, no token, no
 * account, and nothing about a person's purchases is sent anywhere to make a
 * notification appear.
 */
public interface Notifier {
    /** True when this machine can show desktop notifications at all. */
    public val isAvailable: Boolean

    /** Shows one notification. Returns false when it could not be shown. */
    public fun notify(title: String, body: String): Boolean

    public fun notify(reminder: Reminder): Boolean = notify(reminder.title, reminder.body)

    public companion object {
        /** Used when notifications are switched off, and in tests. */
        public val disabled: Notifier = object : Notifier {
            override val isAvailable: Boolean = false
            override fun notify(title: String, body: String): Boolean = false
        }
    }
}
