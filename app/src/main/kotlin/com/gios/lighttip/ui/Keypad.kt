package com.gios.lighttip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp

/**
 * POS-style keypad. Digits push in from the right, so 1-2-3-4 reads as $12.34 —
 * there is no decimal point to mistype, which matters when you are entering a
 * total one-handed while the server waits.
 *
 * It claims the leftover vertical space rather than a fixed fraction: the LPIII is
 * roughly half the height of a normal phone, so anything sized as a percentage of
 * the screen ends up either cramped or off the bottom.
 *
 * **The digits are sized from that leftover, not from a constant.** Reported as
 * "keypad scaling is off - buttons are super small", and the word in that sentence
 * doing the work is *scaling*: the keys were already taking the whole of the space
 * available to them, and printing a 26sp digit in the middle of each one whatever
 * that space turned out to be. A key box is as tall as the screen minus everything
 * above it, which on this phone varies by a factor of two between the Tip tab and
 * the Currency tab, so a size that reads well on one leaves the other looking like
 * a row of small labels floating in empty boxes — a keypad that is technically
 * large and legibly tiny.
 *
 * So the type is measured off the box, with both ends clamped. The floor keeps the
 * digits readable in the tightest tab; the ceiling stops a nearly empty screen
 * producing a numeral so large it reads as an error. In between, a key looks like a
 * key: mostly digit.
 */
@Composable
fun ColumnScope.Keypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "DEL"),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        // Height rather than width, because height is the scarce one here: the keypad is always
        // three keys across and the screen is never the thing rationing that, while everything
        // above it competes for the same vertical space this is measuring.
        val keyHeight = maxHeight.value / rows.size
        val digit = (keyHeight * DIGIT_OF_KEY).coerceIn(DIGIT_MIN, DIGIT_MAX).sp
        // C and DEL are words, not numerals, and three or four letters set at the digit size
        // would be wider than the key. Sized proportionally so they still grow with it.
        val word = (digit.value * WORD_OF_DIGIT).coerceAtLeast(WORD_MIN).sp
        Column(Modifier.fillMaxSize()) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { key ->
                        Key(key, digitSize = digit, wordSize = word) {
                            when (key) {
                                "C" -> onClear()
                                "DEL" -> onBackspace()
                                else -> onDigit(key.toInt())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How much of a key's height the numeral on it is worth. */
private const val DIGIT_OF_KEY = 0.52f
private const val DIGIT_MIN = 26f
private const val DIGIT_MAX = 52f

/** A word key against a numeral key, and the size below which the word stops being readable. */
private const val WORD_OF_DIGIT = 0.55f
private const val WORD_MIN = 14f

@Composable
private fun RowScope.Key(
    label: String,
    digitSize: androidx.compose.ui.unit.TextUnit,
    wordSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    val isDigit = label.length == 1 && label[0].isDigit()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (isDigit) {
                MaterialTheme.typography.titleLarge.copy(fontSize = digitSize)
            } else {
                MaterialTheme.typography.labelLarge.copy(fontSize = wordSize)
            },
            color = Color.White,
        )
    }
}
