package com.gios.lighttip.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.lighttip.data.ReceiptEntity
import com.gios.light.common.hw.WheelScroll
import com.gios.lighttip.ui.theme.Dim
import com.gios.lighttip.util.asMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: TipViewModel,
    onCapture: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = {
                    ModeTitle(mode, open = pickerOpen, onToggle = { pickerOpen = !pickerOpen })
                },
                navigationIcon = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
                actions = {
                    if (mode == CalcMode.Receipt) {
                        IconButton(onClick = onCapture) { Icon(Icons.Default.Add, "Add receipt") }
                    }
                },
            )
        },
    ) { pad ->
        // The picker is stacked over the mode's own content rather than pushing it: on a
        // screen this short, a list that displaced a keypad would relayout the whole
        // screen every time you glanced at it.
        Box(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                if (busy) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color(0xFF303030),
                    )
                }
                when (mode) {
                    CalcMode.Calculator -> CalculatorTab(
                        state = vm.calc.collectAsStateWithLifecycle().value,
                        onKey = vm::onCalcKey,
                        onSendToTip = vm::calcToTip,
                    )

                    CalcMode.Currency -> CurrencyTab(
                        state = vm.currency.collectAsStateWithLifecycle().value,
                        onDigit = vm::currencyDigit,
                        onBackspace = vm::currencyBackspace,
                        onClear = vm::currencyClear,
                        onSwap = vm::currencySwap,
                        onRefresh = { vm.refreshRates() },
                        onPick = vm::currencyPick,
                    )

                    CalcMode.Tip -> TipTab(vm)
                    CalcMode.Receipt -> SplitTab(vm, onCapture = onCapture, onOpen = onOpenReceipt)
                }
            }
            ModeSheet(
                current = mode,
                open = pickerOpen,
                onSelect = { vm.setMode(it); pickerOpen = false },
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

/* ------------------------------------------------------------------ Tip tab */

@Composable
private fun ColumnScope.TipTab(vm: TipViewModel) {
    val state by vm.tip.collectAsStateWithLifecycle()
    var customOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BILL", style = MaterialTheme.typography.labelSmall, color = Dim)
        Box(Modifier.weight(1f))
        Text(
            state.amountCents.asMoney(),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Rule()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Figure("TIP", state.tipCents.asMoney(), Modifier.weight(1f), TextAlign.Start)
        Figure("TOTAL", state.totalCents.asMoney(), Modifier.weight(1f), TextAlign.End)
    }
    Rule()
    PresetGrid(
        selected = state.tipPercent,
        isCustom = state.isCustomPercent,
        onSelect = { vm.setTipPercent(it) },
        onCustom = { customOpen = true },
    )
    Rule()
    Keypad(
        onDigit = { vm.pushDigit(it) },
        onBackspace = { vm.backspace() },
        onClear = { vm.clearAmount() },
    )

    if (customOpen) {
        PercentDialog(
            initial = state.tipPercent,
            onDismiss = { customOpen = false },
            onConfirm = { vm.setTipPercent(it); customOpen = false },
        )
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier, align: TextAlign) {
    Column(
        modifier,
        horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PresetGrid(
    selected: Int,
    isCustom: Boolean,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    val rows = TIP_PRESETS.chunked(3)
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                row.forEach { percent ->
                    Chip(
                        label = "$percent%",
                        selected = !isCustom && percent == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(percent) },
                    )
                }
                repeat(3 - row.size) {
                    Chip(
                        label = if (isCustom) "$selected%" else "OTHER",
                        selected = isCustom,
                        modifier = Modifier.weight(1f),
                        onClick = onCustom,
                    )
                }
            }
            if (rowIndex != rows.lastIndex) Rule()
        }
    }
}

/* ------------------------------------------------------------ Receipt mode */

@Composable
private fun ColumnScope.SplitTab(
    vm: TipViewModel,
    onCapture: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val apiKey by vm.apiKeyState.collectAsStateWithLifecycle()

    if (receipts.isEmpty()) {
        EmptyState(
            if (apiKey.isBlank()) {
                "No receipts yet.\n\nAdd your Anthropic key in Settings,\nthen tap + to photograph a bill."
            } else {
                "No receipts yet.\n\nTap + to photograph a bill."
            },
        )
        return
    }
    // Only one tab is composed at a time, so this needs no active flag: the tip
    // tab has nothing to scroll and isn't there when the split tab is.
    val listState = rememberLazyListState()
    WheelScroll(listState)
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(receipts, key = { it.id }) { receipt ->
            MenuRow(
                label = receipt.merchant,
                sub = receiptDetail(receipt),
                onClick = { onOpen(receipt.id) },
            )
        }
    }
}

private fun receiptDetail(receipt: ReceiptEntity): String = when (receipt.status) {
    ReceiptEntity.STATUS_READING -> "Reading with Claude…"
    ReceiptEntity.STATUS_FAILED -> "Couldn't read — open to retry"
    ReceiptEntity.STATUS_NO_KEY -> "No API key — open to retry"
    else -> receipt.totalCents.asMoney()
}
