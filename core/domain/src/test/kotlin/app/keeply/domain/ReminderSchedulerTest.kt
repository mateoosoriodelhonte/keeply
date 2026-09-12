package app.keeply.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    private val today = LocalDate.of(2026, 9, 12)
    private val now = Instant.parse("2026-09-12T10:00:00Z")

    private fun purchase(name: String, returnDeadline: LocalDate? = null, warrantyEnd: LocalDate? = null, archived: Boolean = false) =
        Purchase(
            id = PurchaseId.new(),
            productName = name,
            merchantId = null,
            merchantName = null,
            purchaseDate = today.minusDays(30),
            price = Money.of("99.00", CurrencyCode.USD),
            categoryId = null,
            receiptDocumentId = null,
            returnWindow = ReturnWindow(ReturnPolicy.Unknown, ReturnPolicySource.NONE, returnDeadline),
            warranty = Warranty(WarrantyTerm.Unknown, WarrantyProvenance.USER_ENTERED, null, warrantyEnd),
            isArchived = archived,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun remindsSevenDaysAndOneDayBeforeAReturnCloses() {
        val prefs = ReminderPreferences(enabled = true)
        val week = purchase("Running shoes", returnDeadline = today.plusDays(7))
        val tomorrow = purchase("Desk chair", returnDeadline = today.plusDays(1))
        val later = purchase("Blender", returnDeadline = today.plusDays(20))

        val due = ReminderScheduler.dueOn(listOf(week, tomorrow, later), today, prefs)
        assertEquals(2, due.size)
        assertTrue(due.any { it.body == "Return window ends in 7 days" })
        assertTrue(due.any { it.body == "Return window ends tomorrow" })
    }

    @Test
    fun remindsAboutWarrantiesWithTheRightWords() {
        val prefs = ReminderPreferences(enabled = true)
        val month = purchase("Coffee machine", warrantyEnd = today.plusDays(30))
        val week = purchase("Laptop", warrantyEnd = today.plusDays(7))

        val due = ReminderScheduler.dueOn(listOf(month, week), today, prefs)
        assertEquals(
            setOf("Warranty expires in 30 days", "Warranty expires this week"),
            due.map { it.body }.toSet(),
        )
    }

    @Test
    fun saysNothingWhenRemindersAreOff() {
        val closing = purchase("Running shoes", returnDeadline = today.plusDays(1))
        assertEquals(emptyList(), ReminderScheduler.dueOn(listOf(closing), today, ReminderPreferences.disabled))
    }

    @Test
    fun ignoresArchivedPurchases() {
        val prefs = ReminderPreferences(enabled = true)
        val archived = purchase("Old phone", returnDeadline = today.plusDays(1), archived = true)
        assertEquals(emptyList(), ReminderScheduler.dueOn(listOf(archived), today, prefs))
    }

    @Test
    fun doesNotNagAboutWindowsThatAlreadyClosed() {
        val prefs = ReminderPreferences(enabled = true)
        val past = purchase("Headphones", returnDeadline = today.minusDays(1))
        assertEquals(emptyList(), ReminderScheduler.dueOn(listOf(past), today, prefs))
    }

    @Test
    fun honoursCustomLeadTimes() {
        val prefs = ReminderPreferences(enabled = true, returnLeadDays = setOf(3))
        val threeDays = purchase("Monitor", returnDeadline = today.plusDays(3))
        val sevenDays = purchase("Keyboard", returnDeadline = today.plusDays(7))

        val due = ReminderScheduler.dueOn(listOf(threeDays, sevenDays), today, prefs)
        assertEquals(1, due.size)
        assertEquals("Monitor", due.single().title)
    }

    @Test
    fun canRaiseBothKindsForOnePurchase() {
        val prefs = ReminderPreferences(enabled = true)
        val both = purchase("Tablet", returnDeadline = today.plusDays(7), warrantyEnd = today.plusDays(30))
        val due = ReminderScheduler.dueOn(listOf(both), today, prefs)
        assertEquals(setOf(ReminderKind.RETURN_WINDOW_CLOSING, ReminderKind.WARRANTY_EXPIRING), due.map { it.kind }.toSet())
    }
}
