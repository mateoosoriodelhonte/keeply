package app.keeply.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a reminder is about. */
public enum class ReminderKind {
    RETURN_WINDOW_CLOSING,
    WARRANTY_EXPIRING,
}

/**
 * A notification Keeply would like to show. Produced locally, delivered locally.
 * There is no server involved at any point.
 */
public data class Reminder(val purchaseId: PurchaseId, val kind: ReminderKind, val dueDate: LocalDate, val title: String, val body: String)

/** Which lead times a person wants to be told about. */
public data class ReminderPreferences(
    val enabled: Boolean = false,
    val returnLeadDays: Set<Int> = setOf(7, 1),
    val warrantyLeadDays: Set<Int> = setOf(30, 7),
) {
    public companion object {
        public val disabled: ReminderPreferences = ReminderPreferences(enabled = false)
    }
}

/**
 * Works out which reminders are due today.
 *
 * Pure and date-injected: the same purchases and the same date always produce the
 * same reminders, which is what makes this testable without waiting a week.
 */
public object ReminderScheduler {
    public fun dueOn(purchases: List<Purchase>, today: LocalDate, preferences: ReminderPreferences): List<Reminder> {
        if (!preferences.enabled) return emptyList()

        return purchases.asSequence()
            .filterNot { it.isArchived }
            .flatMap { purchase -> remindersFor(purchase, today, preferences) }
            .sortedWith(compareBy({ it.dueDate }, { it.title }))
            .toList()
    }

    private fun remindersFor(purchase: Purchase, today: LocalDate, preferences: ReminderPreferences): Sequence<Reminder> = sequence {
        purchase.returnWindow.deadline?.let { deadline ->
            val remaining = ChronoUnit.DAYS.between(today, deadline)
            if (remaining >= 0 && remaining.toInt() in preferences.returnLeadDays) {
                yield(
                    Reminder(
                        purchaseId = purchase.id,
                        kind = ReminderKind.RETURN_WINDOW_CLOSING,
                        dueDate = today,
                        title = purchase.productName,
                        body = returnPhrase(remaining),
                    ),
                )
            }
        }

        purchase.warranty.endDate?.let { end ->
            val remaining = ChronoUnit.DAYS.between(today, end)
            if (remaining >= 0 && remaining.toInt() in preferences.warrantyLeadDays) {
                yield(
                    Reminder(
                        purchaseId = purchase.id,
                        kind = ReminderKind.WARRANTY_EXPIRING,
                        dueDate = today,
                        title = purchase.productName,
                        body = warrantyPhrase(remaining),
                    ),
                )
            }
        }
    }

    private fun returnPhrase(days: Long): String = when (days) {
        0L -> "Return window ends today"
        1L -> "Return window ends tomorrow"
        else -> "Return window ends in $days days"
    }

    private fun warrantyPhrase(days: Long): String = when (days) {
        0L -> "Warranty expires today"
        1L -> "Warranty expires tomorrow"
        in 2L..7L -> "Warranty expires this week"
        else -> "Warranty expires in $days days"
    }
}
