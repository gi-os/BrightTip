package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Free-entry tip percentage on the same keypad.
 *
 * The dialog gets an explicit dark-grey container: the app paints true black with
 * no tonal elevation, so a scrim over black tints nothing and a black-on-black
 * dialog would have no visible edge.
 */
@Composable
fun PercentDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var percent by remember { mutableIntStateOf(initial.coerceIn(0, 100)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Custom tip") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                }
                Column(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF141414))) {
                    Keypad(
                        onDigit = { d ->
                            val next = percent * 10 + d
                            if (next <= 100) percent = next
                        },
                        onBackspace = { percent /= 10 },
                        onClear = { percent = 0 },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(percent) }) { Text("Use", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        },
    )
}
