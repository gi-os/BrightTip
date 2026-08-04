package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.light.common.hw.WheelScroll
import com.gios.lighttip.ui.theme.Dim
import com.gios.lighttip.util.PersonShare
import com.gios.lighttip.util.asMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalsScreen(vm: TipViewModel, receiptId: String, onBack: () -> Unit) {
    val flow = remember(receiptId) { vm.receiptState(receiptId) }
    val state by flow.collectAsStateWithLifecycle()
    val split = state.split
    val tipPercent = state.receipt?.tipPercent ?: DEFAULT_TIP_PERCENT
    val listState = rememberLazyListState()
    WheelScroll(listState)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Totals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(split.shares, key = { it.person.id }) { share -> ShareRow(share) }
            }
            Rule()
            SummaryLine("Items", split.itemsSubtotalCents.asMoney())
            SummaryLine("Tax", split.taxCents.asMoney())
            SummaryLine("Tip ($tipPercent%)", split.tipCents.asMoney())
            SummaryLine("Bill total", split.grandTotalCents.asMoney(), emphasis = true)
            if (!split.allAssigned) {
                SummaryLine(
                    "${split.unassignedCount} unassigned",
                    split.unassignedCents.asMoney(),
                    dim = true,
                )
            }
        }
    }
}

@Composable
private fun ShareRow(share: PersonShare) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                share.person.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${share.itemsCents.asMoney()} items · ${share.taxCents.asMoney()} tax · " +
                    "${share.tipCents.asMoney()} tip",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            share.totalCents.asMoney(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasis: Boolean = false,
    dim: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = if (emphasis) {
            MaterialTheme.typography.bodyLarge
        } else {
            MaterialTheme.typography.bodyMedium
        }
        val color = if (dim) Dim else if (emphasis) Color.White else Dim
        Text(label, style = style, color = color, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color)
    }
}
