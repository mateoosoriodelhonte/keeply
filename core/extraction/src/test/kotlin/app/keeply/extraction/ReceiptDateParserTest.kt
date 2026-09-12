package app.keeply.extraction

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptDateParserTest {
    private val today = LocalDate.of(2026, 9, 12)
    private val parser = ReceiptDateParser(today = { today })

    private fun parse(line: String) = parser.findInLine(line).firstOrNull()?.date

    @Test
    fun readsTheFormatsTillsPrint() {
        assertEquals(LocalDate.of(2026, 5, 9), parse("05/09/2026  17:19"))
        assertEquals(LocalDate.of(2026, 6, 17), parse("Date: 2026-06-17"))
        assertEquals(LocalDate.of(2026, 3, 15), parse("15.03.26 20:29"))
        assertEquals(LocalDate.of(2026, 7, 24), parse("Date of sale   : 24 July 2026"))
        assertEquals(LocalDate.of(2026, 2, 3), parse("Feb 3, 2026"))
        assertEquals(LocalDate.of(2026, 2, 3), parse("3rd February 2026"))
    }

    @Test
    fun usesTheComponentThatCanOnlyBeADay() {
        // 23 cannot be a month, so this is unambiguous whichever convention the shop uses.
        val match = assertNotNull(parser.findInLine("02/23/2026").firstOrNull())
        assertEquals(LocalDate.of(2026, 2, 23), match.date)
        assertFalse(match.ambiguous)
    }

    @Test
    fun saysSoWhenTheDayAndMonthCouldBeEitherWayRound() {
        // A return deadline computed from the wrong month is worse than one the
        // person was asked about.
        val match = assertNotNull(parser.findInLine("03/04/2026").firstOrNull())
        assertTrue(match.ambiguous)
        assertEquals(LocalDate.of(2026, 3, 4), match.date)

        val european = ReceiptDateParser(dayFirst = true, today = { today })
        assertEquals(LocalDate.of(2026, 4, 3), european.findInLine("03/04/2026").first().date)
    }

    @Test
    fun anIdenticalDayAndMonthIsNotAmbiguous() {
        assertFalse(assertNotNull(parser.findInLine("05/05/2026").firstOrNull()).ambiguous)
    }

    @Test
    fun repairsCharactersOcrConfusedForDigits() {
        // Measured against real Tesseract output: it reads a leading zero as @.
        val match = assertNotNull(parser.findInLine("@5/09/2026  17:19").firstOrNull())
        assertEquals(LocalDate.of(2026, 5, 9), match.date)
        assertTrue(match.repaired)
    }

    @Test
    fun expandsTwoDigitYears() {
        assertEquals(LocalDate.of(2026, 3, 15), parse("15.03.26"))
        assertEquals(LocalDate.of(1998, 3, 15), parse("15.03.98"))
    }

    @Test
    fun refusesDatesNobodyCouldHaveShoppedOn() {
        assertNull(parse("12/25/2031"), "a date years in the future is not a purchase date")
        assertNull(parse("01/01/1970"), "a date decades old is not a receipt Keeply should read")
        assertNull(parse("13/45/2026"), "not a date at all")
    }

    @Test
    fun allowsATillClockToBeADayOut() {
        assertEquals(today.plusDays(1), parse(today.plusDays(1).toString()))
    }

    @Test
    fun takesTheDateNearestTheTopAsThePurchaseDate() {
        // Lower down there is usually a return-by date or a printed policy.
        val text = """
            NORTHGATE ELECTRONICS
            09/01/2026  11:04
            HEADPHONES   $249.99
            Return by 10/01/2026
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 9, 1), parser.findPurchaseDate(text)?.date)
    }

    @Test
    fun findsNothingRatherThanGuessing() {
        assertNull(parser.findPurchaseDate("THANK YOU FOR SHOPPING"))
        assertNull(parse("Receipt TXN-214034"))
    }
}
