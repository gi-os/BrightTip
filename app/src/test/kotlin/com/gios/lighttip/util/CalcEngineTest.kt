package com.gios.lighttip.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The engine is deliberately free of Android imports so the whole keypad can be driven
 * from a plain JVM test — every case below is a sequence of key presses and the string
 * the readout should be showing at the end.
 */
class CalcEngineTest {

    /** Drive the engine with a compact key script: digits, `.`, `+-*​/`, `=`, `%`, `~`, `C`, `<`. */
    private fun press(script: String): CalcState {
        var s = CalcState()
        for (c in script) {
            s = when (c) {
                in '0'..'9' -> s.digit(c - '0')
                '.' -> s.decimal()
                '+' -> s.operator(Op.Add)
                '-' -> s.operator(Op.Sub)
                '*' -> s.operator(Op.Mul)
                '/' -> s.operator(Op.Div)
                '=' -> s.equals()
                '%' -> s.percent()
                '~' -> s.negate()
                'C' -> s.clear()
                '<' -> s.backspace()
                ' ' -> s
                else -> error("unknown key '$c'")
            }
        }
        return s
    }

    private fun display(script: String) = press(script).display

    @Test
    fun `starts at zero`() {
        assertEquals("0", CalcState().display)
    }

    /** The whole reason this is BigDecimal and not Double. */
    @Test
    fun `decimal addition is exact`() {
        assertEquals("0.3", display("0.1+0.2="))
    }

    @Test
    fun `exactness holds for money-shaped sums`() {
        assertEquals("0.1", display("1.1-1="))
        assertEquals("8.7", display("2.9*3="))
    }

    @Test
    fun `chaining folds as it goes`() {
        // The running total appears the moment the second operator lands.
        assertEquals("5", display("2+3+"))
        assertEquals("9", display("2+3+4="))
    }

    @Test
    fun `equals repeats the last operation`() {
        assertEquals("5", display("2+3="))
        assertEquals("8", display("2+3=="))
        assertEquals("11", display("2+3==="))
    }

    @Test
    fun `pressing two operators in a row swaps rather than applies`() {
        // 2 × should not have become 2 × 2 by the time 3 arrives.
        assertEquals("6", display("2+*3="))
    }

    @Test
    fun `percent against a pending add is a percentage of the running total`() {
        assertEquals("220", display("200+10%="))
    }

    @Test
    fun `percent on its own divides by a hundred`() {
        assertEquals("0.5", display("50%"))
    }

    @Test
    fun `negate flips the entry without committing anything`() {
        assertEquals("-7", display("7~"))
        assertEquals("3", display("10+7~="))
    }

    @Test
    fun `negating zero stays zero`() {
        assertEquals("0", display("~"))
    }

    @Test
    fun `division by zero errors and only clear recovers`() {
        val errored = press("5/0=")
        assertTrue(errored.error)
        assertEquals("Error", errored.display)
        // Every other key is inert while the error stands.
        assertEquals("Error", errored.digit(7).display)
        assertEquals("Error", errored.operator(Op.Add).display)
        assertEquals("0", errored.clear().display)
    }

    @Test
    fun `backspace only edits an entry in progress`() {
        assertEquals("12", display("123<"))
        assertEquals("0", display("1<"))
        // A result is not a typed entry, so there is nothing to un-type.
        assertEquals("5", display("2+3=<"))
    }

    @Test
    fun `decimal point cannot be doubled`() {
        assertEquals("1.5", display("1.5."))
        assertEquals("0.", display("."))
    }

    @Test
    fun `leading zero is a placeholder not a digit`() {
        assertEquals("5", display("05"))
        assertEquals("0.05", display("0.05"))
    }

    @Test
    fun `entry stops at twelve digits`() {
        val long = display("1234567890123456")
        assertEquals("123,456,789,012", long)
    }

    @Test
    fun `thousands are grouped but the fraction is not`() {
        assertEquals("1,234.5678", display("1234.5678"))
        assertEquals("-1,000", display("1000~"))
    }

    @Test
    fun `results never come back in scientific notation`() {
        // 1e12 × 1e12 would print as 1E+24 straight out of BigDecimal.
        assertFalse(display("1000000*1000000=").contains("E"))
    }

    @Test
    fun `the armed operator is the pending one and clears once typing resumes`() {
        assertEquals(Op.Mul, press("12*").activeOp)
        assertEquals(null, press("12*3").activeOp)
    }

    @Test
    fun `equals with nothing pending is a no-op`() {
        assertEquals("42", display("42="))
    }

    /* ------------------------------------------------- handing off to the tip screen */

    @Test
    fun `money handoff rounds half up to the cent`() {
        assertEquals(101L, BigDecimal("1.005").toMoneyCents())
        assertEquals(1234L, BigDecimal("12.34").toMoneyCents())
        assertEquals(1235L, BigDecimal("12.345").toMoneyCents())
    }

    @Test
    fun `money handoff clamps to the range the tip keypad accepts`() {
        assertEquals(0L, BigDecimal("-5").toMoneyCents())
        assertEquals(99_999_999L, BigDecimal("999999999").toMoneyCents())
    }

    @Test
    fun `a calculated value survives the trip to cents`() {
        assertEquals(30L, press("0.1+0.2=").value.toMoneyCents())
    }
}
