package app.keeply.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParserTest {
    private val parser = MoneyParser(CurrencyCode.USD)

    @Test
    fun readsTheCommonAmericanForms() {
        assertEquals(Money(24999, CurrencyCode.USD), parser.parse("$249.99"))
        assertEquals(Money(24999, CurrencyCode.USD), parser.parse("249.99"))
        assertEquals(Money(24999, CurrencyCode.USD), parser.parse(" $ 249.99 "))
        assertEquals(Money(123456, CurrencyCode.USD), parser.parse("$1,234.56"))
        assertEquals(Money(1200, CurrencyCode.USD), parser.parse("12"))
    }

    @Test
    fun readsEuropeanGrouping() {
        assertEquals(Money(123456, CurrencyCode.EUR), parser.parse("1.234,56 EUR"))
        assertEquals(Money(1250, CurrencyCode.EUR), parser.parse("€12,50"))
        assertEquals(Money(1250, CurrencyCode.EUR), parser.parse("12,5 EUR"))
    }

    @Test
    fun readsRefundsWrittenAsNegatives() {
        assertEquals(Money(-1234, CurrencyCode.USD), parser.parse("(12.34)"))
        assertEquals(Money(-1234, CurrencyCode.USD), parser.parse("12.34-"))
        assertEquals(Money(-1234, CurrencyCode.USD), parser.parse("-$12.34"))
    }

    @Test
    fun prefersAnExplicitCodeOverASymbol() {
        assertEquals(CurrencyCode("CAD"), parser.parse("$12.00 CAD")?.currency)
        assertEquals(CurrencyCode.GBP, parser.parse("£9.99")?.currency)
        assertEquals(CurrencyCode("JPY"), parser.parse("¥1200")?.currency)
    }

    @Test
    fun fallsBackToTheGivenDefaultCurrency() {
        val gbpParser = MoneyParser(CurrencyCode.GBP)
        assertEquals(Money(999, CurrencyCode.GBP), gbpParser.parse("9.99"))
    }

    @Test
    fun treatsThreeTrailingDigitsAsGrouping() {
        // "1,234" is one thousand two hundred and thirty-four, not 1.234.
        assertEquals(Money(123400, CurrencyCode.USD), parser.parse("1,234"))
        assertEquals(Money(123400, CurrencyCode.USD), parser.parse("1.234"))
    }

    @Test
    fun handlesYenWithoutInventingCents() {
        assertEquals(Money(1200, CurrencyCode("JPY")), parser.parse("JPY 1200"))
    }

    @Test
    fun givesUpRatherThanGuessing() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("TOTAL"))
        assertNull(parser.parse("$"))
        assertNull(parser.parse("1.2.3.4"))
    }

    @Test
    fun parsesInAKnownCurrencyWhenTheColumnIsAlreadyIdentified() {
        assertEquals(Money(500, CurrencyCode.EUR), parser.parseIn("5.00", CurrencyCode.EUR))
    }
}
