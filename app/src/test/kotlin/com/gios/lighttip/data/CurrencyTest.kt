package com.gios.lighttip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Conversion is pure arithmetic over a rate map, so it tests without a network or a
 * device. The rates below are a real snapshot from the feed — the point is the exponent
 * handling and the rounding, not the numbers themselves.
 */
class CurrencyTest {

    private val rates = mapOf(
        "USD" to BigDecimal("1"),
        "EUR" to BigDecimal("0.86453"),
        "GBP" to BigDecimal("0.73874"),
        "JPY" to BigDecimal("159.01"),
        "KWD" to BigDecimal("0.30650"),
        "KRW" to BigDecimal("1411.27"),
    )

    @Test
    fun `same currency is the identity, rate table or not`() {
        assertEquals(12_345L, convertMinor(12_345L, "USD", "USD", rates))
        assertEquals(500L, convertMinor(500L, "XYZ", "XYZ", emptyMap()))
    }

    @Test
    fun `dollars to euros`() {
        // $100.00 -> 8645 cents of EUR.
        assertEquals(8_645L, convertMinor(10_000L, "USD", "EUR", rates))
    }

    /**
     * The exponent case. ¥ has no minor unit, so $100.00 is 15,901 *yen*, not 1,590,100
     * of some imaginary sen — a converter that got this wrong would be off by 100×.
     */
    @Test
    fun `dollars to yen respects the zero-decimal exponent`() {
        assertEquals(15_901L, convertMinor(10_000L, "USD", "JPY", rates))
    }

    @Test
    fun `yen to dollars comes back the other way`() {
        // ¥10,000 -> $62.89
        assertEquals(6_289L, convertMinor(10_000L, "JPY", "USD", rates))
    }

    /** Dinars run to three places, so one dollar is 307 fils and not 31. */
    @Test
    fun `dollars to dinar respects the three-decimal exponent`() {
        // 0.30650 KWD, i.e. 306.5 fils — and half-up sends the half away from the bank.
        assertEquals(307L, convertMinor(100L, "USD", "KWD", rates))
    }

    @Test
    fun `cross rates go through USD without either leg losing digits`() {
        // £100.00 -> €117.03, i.e. 100 / 0.73874 * 0.86453.
        assertEquals(11_703L, convertMinor(10_000L, "GBP", "EUR", rates))
    }

    @Test
    fun `large amounts in a small-denomination currency stay exact`() {
        // $1,000,000.00 -> ₩1,411,270,000 — won has no minor unit, so that figure is whole won.
        assertEquals(1_411_270_000L, convertMinor(100_000_000L, "USD", "KRW", rates))
    }

    @Test
    fun `zero converts to zero`() {
        assertEquals(0L, convertMinor(0L, "USD", "JPY", rates))
    }

    @Test
    fun `an unknown currency converts to nothing rather than to a wrong number`() {
        assertNull(convertMinor(100L, "USD", "ZZZ", rates))
        assertNull(convertMinor(100L, "ZZZ", "USD", rates))
        assertNull(unitRateText("USD", "ZZZ", rates))
    }

    @Test
    fun `the unit rate line reads the way it is spoken`() {
        assertEquals("1 USD = 0.8645 EUR", unitRateText("USD", "EUR", rates))
        assertEquals("1 USD = 159.01 JPY", unitRateText("USD", "JPY", rates))
        assertEquals("1 USD = 1 USD", unitRateText("USD", "USD", rates))
    }

    /* ------------------------------------------------------------- formatting */

    @Test
    fun `formatting follows the currency's own exponent`() {
        assertEquals("$1,234.56", Currencies.format(123_456L, "USD"))
        assertEquals("¥1,200", Currencies.format(1_200L, "JPY"))
        assertEquals("1.500 KWD", Currencies.format(1_500L, "KWD"))
    }

    @Test
    fun `codes without a well-known symbol print the code instead`() {
        assertEquals("1,000.00 CHF", Currencies.format(100_000L, "CHF"))
    }

    @Test
    fun `negatives keep the sign outside the symbol`() {
        assertEquals("-$5.00", Currencies.format(-500L, "USD"))
    }

    @Test
    fun `sub-unit amounts pad rather than truncate`() {
        assertEquals("$0.05", Currencies.format(5L, "USD"))
        assertEquals("0.005 BHD", Currencies.format(5L, "BHD"))
    }

    @Test
    fun `exponents match ISO 4217 for the currencies that are not two`() {
        assertEquals(0, Currencies.exponent("JPY"))
        assertEquals(0, Currencies.exponent("krw"))
        assertEquals(3, Currencies.exponent("KWD"))
        assertEquals(2, Currencies.exponent("USD"))
        assertEquals(2, Currencies.exponent("ANYTHING_ELSE"))
    }
}
