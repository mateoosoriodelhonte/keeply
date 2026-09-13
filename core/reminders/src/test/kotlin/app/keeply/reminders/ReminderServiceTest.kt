package app.keeply.reminders

import app.keeply.domain.CurrencyCode
import app.keeply.domain.Money
import app.keeply.domain.Purchase
import app.keeply.domain.PurchaseId
import app.keeply.domain.ReminderKind
import app.keeply.domain.ReminderPreferences
import app.keeply.domain.ReturnPolicy
import app.keeply.domain.ReturnPolicySource
import app.keeply.domain.ReturnWindow
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingNotifier(private val succeeds: Boolean = true) : Notifier {
    val shown = mutableListOf<Pair<String, String>>()
    override val isAvailable: Boolean = true
    override fun notify(title: String, body: String): Boolean {
        if (succeeds) shown += title to body
        return succeeds
    }
}

class ReminderServiceTest {
    private val today = LocalDate.of(2026, 9, 12)
    private val now = Instant.parse("2026-09-12T10:00:00Z")

    private fun purchase(name: String, deadlineInDays: Long) = Purchase(
        id = PurchaseId.new(),
        productName = name,
        merchantId = null,
        merchantName = "Northgate Electronics",
        purchaseDate = today.minusDays(30),
        price = Money.of("99.00", CurrencyCode.USD),
        categoryId = null,
        receiptDocumentId = null,
        returnWindow = ReturnWindow(
            ReturnPolicy.Days(30),
            ReturnPolicySource.USER_ENTERED,
            today.plusDays(deadlineInDays),
        ),
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun tellsSomeoneTheirReturnWindowIsClosing() {
        val notifier = RecordingNotifier()
        val service = ReminderService(notifier, ReminderLog.inMemory())

        val raised = service.runOnce(
            listOf(purchase("Running Shoes", 1)),
            ReminderPreferences(enabled = true),
            today,
        )

        assertEquals(1, raised.size)
        assertEquals(ReminderKind.RETURN_WINDOW_CLOSING, raised.single().kind)
        assertEquals("Running Shoes" to "Return window ends tomorrow", notifier.shown.single())
    }

    @Test
    fun saysNothingWhenRemindersAreOff() {
        val notifier = RecordingNotifier()
        val service = ReminderService(notifier, ReminderLog.inMemory())
        assertEquals(
            emptyList(),
            service.runOnce(listOf(purchase("Running Shoes", 1)), ReminderPreferences.disabled, today),
        )
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun doesNotRepeatItselfEveryTimeKeeplyOpens() {
        // Being reminded is useful. Being reminded of the same thing on every launch
        // is why people turn notifications off.
        val notifier = RecordingNotifier()
        val service = ReminderService(notifier, ReminderLog.inMemory())
        val purchases = listOf(purchase("Running Shoes", 1))
        val preferences = ReminderPreferences(enabled = true)

        assertEquals(1, service.runOnce(purchases, preferences, today).size)
        assertEquals(0, service.runOnce(purchases, preferences, today).size)
        assertEquals(0, service.runOnce(purchases, preferences, today).size)
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun offersItAgainWhenTheNotificationCouldNotBeShown() {
        // A reminder that never appeared has not been delivered, so it must not be
        // recorded as if it had.
        val failing = RecordingNotifier(succeeds = false)
        val service = ReminderService(failing, ReminderLog.inMemory())
        val purchases = listOf(purchase("Running Shoes", 1))
        val preferences = ReminderPreferences(enabled = true)

        assertEquals(0, service.runOnce(purchases, preferences, today).size)

        val working = RecordingNotifier()
        val retried = ReminderService(working, ReminderLog.inMemory())
        assertEquals(1, retried.runOnce(purchases, preferences, today).size)
    }

    @Test
    fun remindsAgainOnALaterDay() {
        val notifier = RecordingNotifier()
        val service = ReminderService(notifier, ReminderLog.inMemory())
        val preferences = ReminderPreferences(enabled = true, returnLeadDays = setOf(7, 1))

        // One purchase, seen on two different days: a week out, then the day before.
        val shoes = listOf(purchase("Running Shoes", 7))
        service.runOnce(shoes, preferences, today)
        service.runOnce(shoes, preferences, today.plusDays(6))

        assertEquals(2, notifier.shown.size)
        assertEquals("Return window ends in 7 days", notifier.shown[0].second)
        assertEquals("Return window ends tomorrow", notifier.shown[1].second)
    }

    @Test
    fun waitsUntilTheNextMorningRatherThanSpinning() {
        val service = ReminderService(RecordingNotifier(), ReminderLog.inMemory())
        val wait = service.untilNextCheck()
        assertTrue(wait.toMillis() > 0, "the next check must be in the future")
        assertTrue(wait.toHours() <= 24, "and within a day, was ${wait.toHours()} hours")
    }
}
