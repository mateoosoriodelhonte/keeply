package app.keeply.reminders

import app.keeply.domain.Purchase
import app.keeply.domain.Reminder
import app.keeply.domain.ReminderPreferences
import app.keeply.domain.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.coroutines.coroutineContext

/** Which reminders have already been shown, so nobody is told twice. */
public interface ReminderLog {
    public fun hasShown(key: String): Boolean
    public fun recordShown(key: String)

    public companion object {
        /** Remembers only for as long as the application runs. */
        public fun inMemory(): ReminderLog = object : ReminderLog {
            private val shown = mutableSetOf<String>()
            override fun hasShown(key: String): Boolean = key in shown
            override fun recordShown(key: String) {
                shown += key
            }
        }
    }
}

/**
 * Tells someone their return window is closing, once.
 *
 * Being reminded is useful. Being reminded of the same thing every time an
 * application starts is why people switch notifications off, so each reminder is
 * recorded once it has been shown and not repeated.
 *
 * Nothing is scheduled with the operating system, and nothing runs when Keeply is
 * closed. This is a loop inside the application, which is the honest shape for a
 * product that promises no background service and no account.
 */
public class ReminderService(
    private val notifier: Notifier,
    private val log: ReminderLog = ReminderLog.inMemory(),
    private val clock: Clock = Clock.systemDefaultZone(),
    /** When in the day to check. Morning, so a deadline that day is still useful. */
    private val checkAt: LocalTime = LocalTime.of(9, 0),
) {
    private val logger = LoggerFactory.getLogger(ReminderService::class.java)

    /**
     * Works out what is due and shows it, skipping anything already shown.
     * Returns what was actually raised, which is what the tests check.
     */
    public fun runOnce(
        purchases: List<Purchase>,
        preferences: ReminderPreferences,
        today: LocalDate = LocalDate.now(clock),
    ): List<Reminder> {
        if (!preferences.enabled) return emptyList()

        val due = ReminderScheduler.dueOn(purchases, today, preferences)
        val raised = mutableListOf<Reminder>()
        due.forEach { reminder ->
            val key = keyFor(reminder, today)
            if (log.hasShown(key)) return@forEach
            if (notifier.notify(reminder)) {
                log.recordShown(key)
                raised += reminder
            } else {
                // Not recorded, so it will be offered again next time. A reminder
                // that failed to appear has not been delivered.
                logger.debug("Could not show a reminder for {}", reminder.purchaseId)
            }
        }
        return raised
    }

    /**
     * Checks once at startup and then once a day.
     *
     * Cancelling the scope stops it. Nothing survives the application closing.
     */
    public fun start(scope: CoroutineScope, purchases: () -> List<Purchase>, preferences: () -> ReminderPreferences): Job = scope.launch {
        runCatching { runOnce(purchases(), preferences()) }
            .onFailure { logger.warn("Reminder check failed", it) }

        while (coroutineContext.isActive) {
            delay(untilNextCheck().toMillis())
            coroutineContext.ensureActive()
            runCatching { runOnce(purchases(), preferences()) }
                .onFailure { logger.warn("Reminder check failed", it) }
        }
    }

    internal fun untilNextCheck(): Duration {
        val now = ZonedDateTime.now(clock)
        val todaysCheck = now.with(checkAt)
        val next = if (now.isBefore(todaysCheck)) todaysCheck else todaysCheck.plusDays(1)
        return Duration.between(now, next)
    }

    /** One reminder per purchase, per kind, per day. */
    private fun keyFor(reminder: Reminder, today: LocalDate): String = "${reminder.purchaseId.value}:${reminder.kind}:$today"
}
