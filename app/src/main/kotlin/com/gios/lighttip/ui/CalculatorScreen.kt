package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gios.lighttip.util.CalcState
import com.gios.lighttip.util.Op
import com.gios.lighttip.ui.theme.Dim

/**
 * Plain four-function calculator. Every key is the same size, because on a matte
 * greyscale panel there is no colour to tell an operator from a digit — the only cue you
 * get is position, so the grid has to be perfectly regular and the operators have to be
 * in the column your thumb already expects them in.
 */
@Composable
fun ColumnScope.CalculatorTab(
    state: CalcState,
    onKey: (CalcKey) -> Unit,
    onSendToTip: () -> Unit,
) {
    Readout(state)
    Rule()
    // The pending operation, spelled out, so a chain you started thirty seconds ago is
    // still legible: "128 ×".
    PendingLine(state)
    Rule()
    CalcKeypad(state, onKey)
    Rule()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onSendToTip)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("USE AS BILL", style = MaterialTheme.typography.labelSmall, color = Dim)
        Box(Modifier.weight(1f))
        Text("TIP →", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun Readout(state: CalcState) {
    val text = state.display
    // Step the type down as the number grows rather than ellipsing it. A truncated total
    // is a wrong total, and there is no horizontal scroll on a screen this size.
    val size = when {
        text.length <= 9 -> 44
        text.length <= 12 -> 36
        text.length <= 15 -> 29
        else -> 23
    }.sp
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = size),
            color = Color.White,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun PendingLine(state: CalcState) {
    val left = state.accumulator
    val label = when {
        state.error -> "Can't divide by zero — press C"
        left != null && state.pending != null ->
            "${left.stripTrailingZeros().toPlainString()} ${state.pending.symbol}"

        else -> " "
    }
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = Dim,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        textAlign = TextAlign.End,
    )
}

/** One key press. Kept as a type rather than a string so the `when` in the VM is total. */
sealed interface CalcKey {
    data class Digit(val value: Int) : CalcKey
    data class Operator(val op: Op) : CalcKey
    data object Decimal : CalcKey
    data object Equals : CalcKey
    data object Clear : CalcKey
    data object Backspace : CalcKey
    data object Percent : CalcKey
    data object Negate : CalcKey
}

@Composable
private fun ColumnScope.CalcKeypad(state: CalcState, onKey: (CalcKey) -> Unit) {
    val rows: List<List<Pair<String, CalcKey>>> = listOf(
        listOf(
            "DEL" to CalcKey.Backspace,
            "C" to CalcKey.Clear,
            "%" to CalcKey.Percent,
            "÷" to CalcKey.Operator(Op.Div),
        ),
        listOf(
            "7" to CalcKey.Digit(7),
            "8" to CalcKey.Digit(8),
            "9" to CalcKey.Digit(9),
            "×" to CalcKey.Operator(Op.Mul),
        ),
        listOf(
            "4" to CalcKey.Digit(4),
            "5" to CalcKey.Digit(5),
            "6" to CalcKey.Digit(6),
            "−" to CalcKey.Operator(Op.Sub),
        ),
        listOf(
            "1" to CalcKey.Digit(1),
            "2" to CalcKey.Digit(2),
            "3" to CalcKey.Digit(3),
            "+" to CalcKey.Operator(Op.Add),
        ),
        listOf(
            "±" to CalcKey.Negate,
            "0" to CalcKey.Digit(0),
            "." to CalcKey.Decimal,
            "=" to CalcKey.Equals,
        ),
    )
    // Takes the leftover height rather than a fixed fraction — same reason as the tip
    // keypad: the LPIII is about half the height of a normal phone.
    Column(Modifier.fillMaxWidth().weight(1f)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { (label, key) ->
                    // The waiting operator inverts, so after `128 ×` you can see which one
                    // you pressed without re-reading the line above.
                    val armed = key is CalcKey.Operator && key.op == state.activeOp
                    CalcButton(label, armed) { onKey(key) }
                }
            }
            if (index != rows.lastIndex) Rule()
        }
    }
}

@Composable
private fun RowScope.CalcButton(label: String, armed: Boolean, onClick: () -> Unit) {
    val isDigit = label.length == 1 && label[0].isDigit()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(if (armed) Color.White else Color.Black)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (isDigit) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (armed) Color.Black else Color.White,
            maxLines = 1,
        )
    }
}
