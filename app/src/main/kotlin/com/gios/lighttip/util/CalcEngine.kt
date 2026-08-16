package com.gios.lighttip.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The four-function calculator behind the Calculator mode.
 *
 * Everything here is [BigDecimal], never [Double], for the same reason the rest of the
 * app holds money in `Long` cents: this app exists to be trusted with a bill. A binary
 * float cannot represent 0.1, so `0.1 + 0.2` reads `0.30000000000000004`, and a person
 * doing bar arithmetic on a matte e-ink panel has no way to know whether the tail is the
 * calculator's fault or theirs. Exact decimal removes the question.
 *
 * [CalcState] is an immutable value: every key produces a whole new state, which makes
 * the whole engine testable without Android and lets Compose diff it for free.
 */

/** 12 significant digits — what fits the readout before it has to shrink. */
private val MC = MathContext(12, RoundingMode.HALF_UP)

/** Beyond this the readout is unreadable at arm's length, so entry just stops. */
private const val MAX_ENTRY_DIGITS = 12

enum class Op(val symbol: String) {
    Add("+"),
    Sub("−"),
    Mul("×"),
    Div("÷"),
}

/**
 * @property entry the digits being typed right now, verbatim, so a trailing "." or the
 *   zeros in "1.50" survive on screen until the entry is committed.
 * @property accumulator the left-hand side of a pending operation.
 * @property pending the operator waiting on a right-hand side.
 * @property repeat the last (operator, operand) pair, so pressing `=` again repeats it —
 *   `2 + 3 = = =` walks 5, 8, 11, the behaviour every physical calculator has.
 * @property error set when the last operation was undefined; every key except `C` is
 *   then ignored, so a division by zero cannot silently poison a later total.
 */
data class CalcState(
    val entry: String = "0",
    val accumulator: BigDecimal? = null,
    val pending: Op? = null,
    val typing: Boolean = false,
    val repeat: Pair<Op, BigDecimal>? = null,
    val error: Boolean = false,
) {
    /** What the big readout shows. */
    val display: String get() = if (error) "Error" else formatForDisplay(entry)

    /** The value currently on screen, for handing to another mode. */
    val value: BigDecimal get() = entry.toBigDecimalOrNull() ?: BigDecimal.ZERO

    /** Lights the operator key that is waiting, the way a hardware calculator does. */
    val activeOp: Op? get() = if (typing) null else pending
}

/* -------------------------------------------------------------------- keys */

fun CalcState.digit(d: Int): CalcState {
    if (error) return this
    if (typing) {
        // The cap is on digits, so the sign and the point don't cost you a place.
        if (entry.count(Char::isDigit) >= MAX_ENTRY_DIGITS) return this
        // "0" is a placeholder, not a typed digit — replace it. "0." is a real prefix, keep it.
        val next = if (entry == "0") "$d" else if (entry == "-0") "-$d" else entry + d
        return copy(entry = next)
    }
    return copy(entry = "$d", typing = true)
}

fun CalcState.decimal(): CalcState {
    if (error) return this
    if (!typing) return copy(entry = "0.", typing = true)
    return if (entry.contains('.')) this else copy(entry = "$entry.")
}

/**
 * Sign flips the thing on screen — the typed entry mid-entry, otherwise the result.
 * It is not an operator: it never commits a pending operation.
 */
fun CalcState.negate(): CalcState {
    if (error) return this
    val flipped = when {
        entry == "0" || entry == "0." -> entry
        entry.startsWith("-") -> entry.removePrefix("-")
        else -> "-$entry"
    }
    return copy(entry = flipped)
}

/**
 * `%` divides by 100, and against a pending `+`/`−` reads as "percent *of* the running
 * total": `200 + 10 %` gives 220, not 200.1. That is the convention every pocket
 * calculator uses and the one a person adding a tip in their head expects.
 */
fun CalcState.percent(): CalcState {
    if (error) return this
    val current = value
    val result = when {
        pending == Op.Add || pending == Op.Sub ->
            (accumulator ?: BigDecimal.ZERO).multiply(current, MC).divide(HUNDRED, MC)

        else -> current.divide(HUNDRED, MC)
    }
    return copy(entry = result.plain(), typing = false)
}

/** Backspace only touches an entry in progress; there is nothing to un-type in a result. */
fun CalcState.backspace(): CalcState {
    if (error) return CalcState()
    if (!typing) return this
    val trimmed = entry.dropLast(1)
    return when {
        trimmed.isEmpty() || trimmed == "-" -> copy(entry = "0", typing = false)
        else -> copy(entry = trimmed)
    }
}

fun CalcState.clear(): CalcState = CalcState()

/**
 * Chaining folds as it goes: `2 + 3 + 4` shows 5 the moment the second `+` lands, which
 * is how a running tally is meant to read. Pressing two operators in a row just swaps the
 * pending one instead of applying the same number twice.
 */
fun CalcState.operator(op: Op): CalcState {
    if (error) return this
    if (!typing && pending != null) return copy(pending = op)
    val left = accumulator
    val right = value
    val folded = if (left != null && pending != null) {
        apply(left, pending, right) ?: return copy(error = true)
    } else {
        right
    }
    return copy(
        entry = folded.plain(),
        accumulator = folded,
        pending = op,
        typing = false,
        repeat = null,
    )
}

/**
 * `=` with nothing pending repeats the previous operation, so `2 + 3 = =` reaches 8. The
 * accumulator is cleared afterwards: the result is now the left-hand side of whatever
 * comes next, held in [entry], and leaving a stale accumulator behind is how calculators
 * end up applying an operation twice.
 */
fun CalcState.equals(): CalcState {
    if (error) return this
    val (op, operand) = when {
        pending != null -> pending to value
        repeat != null -> repeat
        else -> return copy(typing = false)
    }
    val left = if (pending != null) accumulator ?: value else value
    val result = apply(left, op, operand) ?: return copy(error = true)
    return copy(
        entry = result.plain(),
        accumulator = null,
        pending = null,
        typing = false,
        repeat = op to operand,
    )
}

/* ------------------------------------------------------------------- maths */

private val HUNDRED = BigDecimal(100)

/** Null means undefined — only division by zero, today. */
private fun apply(left: BigDecimal, op: Op, right: BigDecimal): BigDecimal? = when (op) {
    Op.Add -> left.add(right, MC)
    Op.Sub -> left.subtract(right, MC)
    Op.Mul -> left.multiply(right, MC)
    Op.Div -> if (right.signum() == 0) null else left.divide(right, MC)
}

/**
 * Plain string, no exponent, no trailing zeros — `1E+2` is not something you want to read
 * off a calculator, and `stripTrailingZeros` alone produces exactly that.
 */
private fun BigDecimal.plain(): String {
    val stripped = stripTrailingZeros()
    val scaled = if (stripped.scale() < 0) stripped.setScale(0) else stripped
    return scaled.toPlainString()
}

/**
 * Group the integer part with commas. The fractional part is left alone: digits after the
 * point are read one at a time, and grouping them is actively misleading.
 */
internal fun formatForDisplay(raw: String): String {
    val negative = raw.startsWith("-")
    val body = raw.removePrefix("-")
    val whole = body.substringBefore('.')
    val rest = if (body.contains('.')) "." + body.substringAfter('.') else ""
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-" else "") + grouped + rest
}

/**
 * Hand the calculator's value to a money field. Rounds half-up to the cent, because the
 * only sane thing to do with $1.005 on a bill is charge $1.01, and clamps to the same
 * ceiling the tip keypad enforces so a wild multiply cannot overflow the readout.
 */
fun BigDecimal.toMoneyCents(): Long =
    multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP)
        .toBigInteger().toLong().coerceIn(0L, 99_999_999L)
