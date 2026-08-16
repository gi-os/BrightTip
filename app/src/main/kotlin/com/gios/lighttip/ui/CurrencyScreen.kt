package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gios.light.common.hw.WheelScroll
import com.gios.lighttip.data.Currencies
import com.gios.lighttip.ui.theme.Dim
import com.gios.lighttip.ui.theme.Faint

/**
 * What the currency screen is showing right now. Assembled in the view model so this file
 * has no arithmetic in it at all.
 */
data class CurrencyUiState(
    val from: String = "USD",
    val to: String = "EUR",
    val amountMinor: Long = 0L,
    val convertedMinor: Long? = null,
    val unitRate: String? = null,
    val ageLabel: String = "No rates yet",
    val codes: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val stale: Boolean = false,
)

/**
 * Convert an amount between two currencies.
 *
 * The rate line and its age are on screen permanently rather than behind a tap. Rates go
 * off, this thing is at its most useful exactly where there is no signal to refresh them,
 * and a converter that shows a number without saying how old it is invites you to trust
 * a figure from three weeks ago.
 */
@Composable
fun ColumnScope.CurrencyTab(
    state: CurrencyUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onPick: (slot: CurrencySlot, code: String) -> Unit,
) {
    var picking by remember { mutableStateOf<CurrencySlot?>(null) }

    val slot = picking
    if (slot != null) {
        CurrencyPicker(
            title = if (slot == CurrencySlot.From) "Convert from" else "Convert to",
            codes = state.codes,
            selected = if (slot == CurrencySlot.From) state.from else state.to,
            onPick = { onPick(slot, it); picking = null },
            onDismiss = { picking = null },
        )
        return
    }

    AmountRow(
        code = state.from,
        text = Currencies.format(state.amountMinor, state.from),
        emphasis = false,
        onClick = { picking = CurrencySlot.From },
    )
    // Tapping the divider swaps the two — the single most common thing you do here after
    // typing, and it costs no vertical space to put it in the rule you already have.
    SwapRule(onSwap)
    AmountRow(
        code = state.to,
        text = state.convertedMinor?.let { Currencies.format(it, state.to) } ?: "—",
        emphasis = true,
        onClick = { picking = CurrencySlot.To },
    )
    Rule()
    RateLine(state, onRefresh)
    Rule()
    Keypad(onDigit = onDigit, onBackspace = onBackspace, onClear = onClear)
}

enum class CurrencySlot { From, To }

@Composable
private fun AmountRow(code: String, text: String, emphasis: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(code, style = MaterialTheme.typography.labelSmall, color = Color.White)
            Text("TAP", style = MaterialTheme.typography.labelSmall, color = Faint)
        }
        Box(Modifier.weight(1f))
        Text(
            text,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = if (text.length <= 10) 38.sp else 28.sp,
            ),
            color = if (emphasis) Color.White else Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwapRule(onSwap: () -> Unit) {
    Rule()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onSwap)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SWAP ⇅", style = MaterialTheme.typography.labelSmall, color = Dim)
    }
    Rule()
}

@Composable
private fun RateLine(state: CurrencyUiState, onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onRefresh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                state.unitRate ?: "No rate for this pair",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                state.ageLabel,
                style = MaterialTheme.typography.labelSmall,
                // Stale rates read brighter, not dimmer. The warning has to be the thing
                // that catches the eye, not the thing that fades into the background.
                color = if (state.stale) Color.White else Dim,
                maxLines = 1,
            )
        }
        Text(
            if (state.refreshing) "…" else "REFRESH",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Full-screen currency list with a filter box. Popular codes float to the top when the
 * box is empty; typing matches either the code or the name, so "yen" and "JPY" both land.
 */
@Composable
private fun CurrencyPicker(
    title: String,
    codes: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val shown = remember(codes, needle) {
        val matched = if (needle.isEmpty()) {
            codes
        } else {
            codes.filter { code ->
                code.lowercase().contains(needle) ||
                    Currencies.name(code)?.lowercase()?.contains(needle) == true
            }
        }
        if (needle.isEmpty()) {
            val popular = Currencies.POPULAR.filter { it in matched }
            popular + matched.filterNot { it in popular }
        } else {
            matched
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Dim)
            Box(Modifier.weight(1f))
            Text(
                "CANCEL",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search", color = Faint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )
        Rule(Modifier.padding(top = 8.dp))
        if (shown.isEmpty()) {
            EmptyState("Nothing matches “$query”.")
            return@Column
        }
        val listState = rememberLazyListState()
        WheelScroll(listState)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(shown, key = { it }) { code ->
                CurrencyRow(code, selected = code == selected, onClick = { onPick(code) })
                Rule()
            }
        }
    }
}

@Composable
private fun CurrencyRow(code: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White else Color.Black)
            .clickable(role = Role.Button, onClick = onClick)
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            code,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color.Black else Color.White,
        )
        Text(
            Currencies.name(code).orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color(0xFF444444) else Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}
