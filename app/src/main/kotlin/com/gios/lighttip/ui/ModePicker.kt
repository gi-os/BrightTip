package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gios.lighttip.ui.theme.Dim

/**
 * The four things this app is. Order is the order they appear in the picker, and the
 * ordinal is what gets persisted, so new modes go on the end.
 */
enum class CalcMode(val title: String, val subtitle: String) {
    Calculator("Calculator", "Plain arithmetic"),
    Currency("Currency", "Convert between currencies"),
    Tip("Tip", "Bill, percentage, total"),
    Receipt("Receipt", "Photograph and split a bill"),
    ;

    companion object {
        fun ofOrdinal(index: Int): CalcMode = entries.getOrElse(index) { Calculator }
    }
}

/**
 * The title-bar half of the picker: the current mode's name with a chevron, tappable as
 * one target. Goes in the `TopAppBar` `title` slot.
 */
@Composable
fun ModeTitle(mode: CalcMode, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mode.title, color = Color.White)
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (open) "Close mode list" else "Choose mode",
            tint = Color.White,
            modifier = Modifier.padding(start = 4.dp).size(22.dp),
        )
    }
}

/**
 * The list half: a full-width sheet that pushes down from under the bar, one row per
 * mode, a check against the active one.
 *
 * It overlays the mode's own content rather than displacing it — the LPIII screen is
 * roughly half the height of a normal phone, and a list that shoved a keypad off the
 * bottom every time you opened it would be worse than no picker at all. A scrim below
 * the list catches the tap that closes it, so anywhere outside is a dismiss.
 */
@Composable
fun ModeSheet(
    current: CalcMode,
    open: Boolean,
    onSelect: (CalcMode) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    Column(Modifier.fillMaxSize()) {
        // No open/close animation on purpose: the panel is e-ink-like and partial refreshes
        // of a sliding list smear. It appears and it disappears.
        Column(Modifier.fillMaxWidth().background(Color.Black)) {
            CalcMode.entries.forEach { mode ->
                ModeRow(mode, selected = mode == current, onClick = { onSelect(mode) })
                Rule()
            }
        }
        // Everything under the list is a dismiss target. `weight`, not `fillMaxSize`: the
        // list above already took part of the column, and a second child asking for the
        // whole height would measure past the bottom of the screen.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xE6000000))
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun ModeRow(mode: CalcMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White else Color.Black)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check occupies its width whether or not it is drawn, so the labels of the
        // four rows line up in a column instead of jumping as the selection moves.
        Box(Modifier.width(30.dp)) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                mode.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Color.Black else Color.White,
            )
            Text(
                mode.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color(0xFF444444) else Dim,
            )
        }
    }
}
