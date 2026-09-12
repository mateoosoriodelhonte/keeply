package app.keeply.domain

import java.math.BigDecimal
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun addsAndSubtractsExactly() {
        val a = Money.of("0.10", CurrencyCode.USD)
        val b = Money.of("0.20", CurrencyCode.USD)
        // The case binary floating point gets wrong.
        assertEquals(Money.of("0.30", CurrencyCode.USD), a + b)
        assertEquals(Money.of("-0.10", CurrencyCode.USD), a - b)
    }

    @Test
    fun sumsAHundredTinyAmountsWithoutDrift() {
        val cent = Money(1, CurrencyCode.USD)
        val total = (1..100).fold(Money.zero(CurrencyCode.USD)) { acc, _ -> acc + cent }
        assertEquals(Money.of("1.00", CurrencyCode.USD), total)
    }

    @Test
    fun refusesToMixCurrencies() {
        val dollars = Money.of("10.00", CurrencyCode.USD)
        val euros = Money.of("10.00", CurrencyCode.EUR)
        assertFailsWith<IllegalArgumentException> { dollars + euros }
        assertFailsWith<IllegalArgumentException> { dollars.compareTo(euros) }
    }

    @Test
    fun respectsCurrenciesWithoutMinorUnits() {
        val yen = CurrencyCode("JPY")
        assertEquals(0, yen.minorUnitDigits)
        assertEquals(Money(1200, yen), Money.of("1200", yen))
        assertEquals("1200", Money(1200, yen).toPlainString())
    }

    @Test
    fun roundsHalfUpToTheCurrencyPrecision() {
        assertEquals(Money(1235, CurrencyCode.USD), Money.of(BigDecimal("12.345"), CurrencyCode.USD))
        assertEquals(Money(1234, CurrencyCode.USD), Money.of(BigDecimal("12.344"), CurrencyCode.USD))
    }

    @Test
    fun formatsForPeopleAndForMachines() {
        val price = Money.of("249.99", CurrencyCode.USD)
        assertEquals("249.99", price.toPlainString())
        assertEquals("$249.99", price.format(Locale.US))
    }

    @Test
    fun rejectsCurrencyCodesThatAreNotCurrencies() {
        assertFailsWith<IllegalArgumentException> { CurrencyCode("US") }
        assertFailsWith<IllegalArgumentException> { CurrencyCode("usd") }
        assertEquals(null, CurrencyCode.orNull("XYZ"))
        assertEquals(CurrencyCode.USD, CurrencyCode.orNull("usd"))
    }

    @Test
    fun detectsOverflowRatherThanWrappingAround() {
        val huge = Money(Long.MAX_VALUE, CurrencyCode.USD)
        assertFailsWith<ArithmeticException> { huge + Money(1, CurrencyCode.USD) }
    }

    @Test
    fun reportsAbsoluteDifferenceForDuplicateMatching() {
        val a = Money.of("42.00", CurrencyCode.USD)
        val b = Money.of("42.01", CurrencyCode.USD)
        assertEquals(1L, a.absoluteDifference(b))
        assertEquals(1L, b.absoluteDifference(a))
        assertTrue(a < b)
    }
}
