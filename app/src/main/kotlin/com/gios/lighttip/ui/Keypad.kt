package com.gios.lighttip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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

/**
 * POS-style keypad. Digits push in from the right, so 1-2-3-4 reads as $12.34 —
 * there is no decimal point to mistype, which matters when you are entering a
 * total one-handed while the server waits.
 *
 * It claims the leftover vertical space rather than a fixed fraction: the LPIII is
 * roughly half the height of a normal phone, so anything sized as a percentage of
 * the screen ends up either cramped or off the bottom.
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
    Column(Modifier.fillMaxWidth().weight(1f)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { key ->
                    Key(key) {
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

@Composable
private fun RowScope.Key(label: String, onClick: () -> Unit) {
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
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = Color.White,
        )
    }
}
