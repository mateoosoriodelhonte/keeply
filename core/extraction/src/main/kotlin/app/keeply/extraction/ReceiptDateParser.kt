package app.keeply.extraction

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date found on a receipt, with an honest account of how sure we can be. */
public data class DateMatch(
    val date: LocalDate,
    val raw: String,
    val lineIndex: Int,
    /**
     * True when the day and month could be read either way round, as in 03/04/2026.
     * The interface must show these for review rather than stating them.
     */
    val ambiguous: Boolean,
    val repaired: Boolean,
)

/**
 * Finds the purchase date.
 *
 * Receipts write dates every way there is, and OCR turns zeroes into letters, so
 * this tries a fixed list of layouts against repaired text and checks the result is
 * a date somebody could actually have shopped on.
 *
 * Where day and month could be swapped, it says so instead of picking one quietly.
 * A return deadline computed from the wrong month is worse than one the person was
 * asked to confirm.
 */
public class ReceiptDateParser(
    /** Which way round to read an ambiguous date. Still marked ambiguous either way. */
    private val dayFirst: Boolean = false,
    private val today: () -> LocalDate = LocalDate::now,
    /**
     * How far ahead a date may be. Two days for a purchase date, allowing for a
     * till clock that is out and for importing across a timezone. A printed
     * return-by or warranty date is in the future by design, so callers looking for
     * those pass a wider window.
     */
    private val futureToleranceDays: Long = PURCHASE_FUTURE_TOLERANCE_DAYS,
) {
    public fun findAll(text: String): List<DateMatch> = text.lines().flatMapIndexed { index, line -> findInLine(line, index) }

    /** The most likely purchase date: the earliest plausible one near the top. */
    public fun findPurchaseDate(text: String): DateMatch? {
        val matches = findAll(text)
        if (matches.isEmpty()) return null
        // Receipts print the transaction date near the top; anything further down is
        // usually a return-by date or a printed policy.
        return matches.minByOrNull { it.lineIndex }
    }

    public fun findInLine(line: String, lineIndex: Int = 0): List<DateMatch> {
        val results = mutableListOf<DateMatch>()

        NUMERIC.findAll(line).forEach { match ->
            val repaired = OcrRepair.digitsOnly(match.value)
            val parts = repaired.split('/', '-', '.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return@forEach
            // A layout heuristic rather than a guess: a till that writes 08.10.26 is
            // almost always writing day first, and one that writes 08/10/26 is
            // usually writing month first. Ambiguous dates are still flagged either way.
            val dayFirstHere = if (repaired.contains('.')) true else dayFirst
            interpret(parts, dayFirstHere)?.let { (date, ambiguous) ->
                results += DateMatch(date, match.value, lineIndex, ambiguous, repaired != match.value)
            }
        }

        TEXTUAL_FORMATS.forEach { formatter ->
            TEXTUAL.findAll(line).forEach { match ->
                val cleaned = match.value.replace(ORDINAL, "$1").trim()
                runCatching { LocalDate.parse(cleaned, formatter) }.getOrNull()?.let { date ->
                    if (plausible(date) && results.none { it.date == date }) {
                        results += DateMatch(date, match.value, lineIndex, ambiguous = false, repaired = false)
                    }
                }
            }
        }

        ISO.findAll(line).forEach { match ->
            val repaired = OcrRepair.digitsOnly(match.value)
            runCatching { LocalDate.parse(repaired) }.getOrNull()?.let { date ->
                if (plausible(date) && results.none { it.date == date }) {
                    results += DateMatch(date, match.value, lineIndex, ambiguous = false, repaired = repaired != match.value)
                }
            }
        }

        return results
    }

    /**
     * Decides what three numbers mean.
     *
     * A component over twelve can only be a day, which settles most receipts. When
     * neither settles it, the configured preference is used and the result is
     * flagged so a person is asked.
     */
    private fun interpret(parts: List<Int>, dayFirstHere: Boolean): Pair<LocalDate, Boolean>? {
        val (first, second, third) = parts

        // yyyy/MM/dd
        if (first > 31) {
            return build(first, second, third)?.let { it to false }
        }

        val year = expandYear(third) ?: return null
        val firstCouldBeMonth = first in 1..12
        val secondCouldBeMonth = second in 1..12

        return when {
            firstCouldBeMonth && !secondCouldBeMonth -> build(year, first, second)?.let { it to false }

            !firstCouldBeMonth && secondCouldBeMonth -> build(year, second, first)?.let { it to false }

            firstCouldBeMonth && secondCouldBeMonth -> {
                val date = if (dayFirstHere) build(year, second, first) else build(year, first, second)
                // Same day and month, such as 05/05, reads the same either way.
                date?.let { it to (first != second) }
            }

            else -> null
        }?.takeIf { plausible(it.first) }
    }

    private fun build(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

    /** Two-digit years: `26` is this century, `98` is the last one. */
    private fun expandYear(value: Int): Int? = when {
        value in 1990..2100 -> value
        value in 0..69 -> 2000 + value
        value in 70..99 -> 1900 + value
        else -> null
    }

    /**
     * A purchase date has to be one somebody could have shopped on: not in the
     * future, and not before receipts were worth keeping.
     */
    private fun plausible(date: LocalDate): Boolean {
        val now = today()
        return !date.isAfter(now.plusDays(futureToleranceDays)) && date.isAfter(now.minusYears(MAX_AGE_YEARS))
    }

    public companion object {
        /** A till clock can be a day out, and someone may import a receipt from a different timezone. */
        public const val PURCHASE_FUTURE_TOLERANCE_DAYS: Long = 2L

        /** For dates that are meant to be ahead: a printed return-by or warranty end. */
        public const val FUTURE_DATE_TOLERANCE_DAYS: Long = 3_650L

        private const val MAX_AGE_YEARS = 30L

        // \b cannot be used: the character classes include @ and |, which are not
        // word characters, so a date OCR read as "@5/09/2026" would never match.
        private const val DIGIT_LIKE = "[\\dOolISZB@|]"
        private val NUMERIC = Regex("""(?<![\w@|])$DIGIT_LIKE{1,4}[/.\-]$DIGIT_LIKE{1,2}[/.\-]$DIGIT_LIKE{2,4}(?![\w@|])""")
        private val ISO = Regex("""(?<![\w@|])$DIGIT_LIKE{4}-$DIGIT_LIKE{2}-$DIGIT_LIKE{2}(?![\w@|])""")
        private val TEXTUAL = Regex(
            """\b\d{1,2}(?:st|nd|rd|th)?\s+[A-Z][a-z]{2,8}\.?\s+\d{4}\b""" +
                """|\b[A-Z][a-z]{2,8}\.?\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4}\b""",
        )
        private val ORDINAL = Regex("""(\d{1,2})(?:st|nd|rd|th)""")

        private val TEXTUAL_FORMATS: List<DateTimeFormatter> = listOf(
            "d MMMM yyyy",
            "d MMM yyyy",
            "MMMM d yyyy",
            "MMM d yyyy",
            "MMMM d, yyyy",
            "MMM d, yyyy",
            "d MMMM, yyyy",
        ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    }
}
