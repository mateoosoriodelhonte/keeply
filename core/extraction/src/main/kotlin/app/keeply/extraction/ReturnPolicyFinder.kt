package app.keeply.extraction

import app.keeply.domain.ReturnPolicy
import java.time.LocalDate

/** A returns line printed on the receipt, with the exact words it was read from. */
public data class PrintedReturnPolicy(val policy: ReturnPolicy, val evidence: String)

/**
 * Reads a returns line off the receipt, when the shop prints one.
 *
 * This is the only kind of return window Keeply can honestly attribute to a shop,
 * because it came from the shop's own paper. Anything else is the person's saved
 * rule, and is labelled that way.
 *
 * Deliberately conservative: it needs both a word about returns and a number of
 * days on the same line. "Thank you for shopping with us, see our returns policy
 * online" yields nothing, which is correct.
 */
public class ReturnPolicyFinder(private val today: () -> LocalDate = LocalDate::now) {

    public fun find(text: String): PrintedReturnPolicy? {
        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (!RETURN_WORD.containsMatchIn(line)) return@forEach

            DAYS.find(line)?.let { match ->
                val days = match.groupValues[1].toIntOrNull() ?: return@let
                if (days in 1..ReturnPolicy.MAX_DAYS) {
                    return PrintedReturnPolicy(ReturnPolicy.Days(days), line)
                }
            }

            MONTHS.find(line)?.let { match ->
                val months = match.groupValues[1].toIntOrNull() ?: return@let
                if (months in 1..MAX_MONTHS) {
                    return PrintedReturnPolicy(ReturnPolicy.Days(months * DAYS_PER_MONTH), line)
                }
            }

            BY_DATE.find(line)?.let { match ->
                // A printed return-by date is ahead of today by design.
                ReceiptDateParser(
                    today = today,
                    futureToleranceDays = ReceiptDateParser.FUTURE_DATE_TOLERANCE_DAYS,
                ).findInLine(match.value).firstOrNull()?.let { date ->
                    return PrintedReturnPolicy(ReturnPolicy.Until(date.date), line)
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_MONTHS = 24
        const val DAYS_PER_MONTH = 30

        val RETURN_WORD = Regex("""\b(return|returns|refund|refunds|exchange|exchanges)\b""", RegexOption.IGNORE_CASE)
        val DAYS = Regex("""\b(\d{1,3})\s*(?:calendar\s+)?days?\b""", RegexOption.IGNORE_CASE)
        val MONTHS = Regex("""\b(\d{1,2})\s*months?\b""", RegexOption.IGNORE_CASE)
        val BY_DATE = Regex("""\b(?:by|before|until|through)\s+(\S+)""", RegexOption.IGNORE_CASE)
    }
}
