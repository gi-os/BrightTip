package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.lighttip.data.ItemEntity
import com.gios.lighttip.data.PersonEntity
import com.gios.lighttip.data.ReceiptEntity
import com.gios.lighttip.ui.theme.Dim
import com.gios.lighttip.util.asMoney
import com.gios.lighttip.util.initialsOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    vm: TipViewModel,
    receiptId: String,
    onPeople: () -> Unit,
    onTotals: () -> Unit,
    onBack: () -> Unit,
) {
    val flow = remember(receiptId) { vm.receiptState(receiptId) }
    val state by flow.collectAsStateWithLifecycle()
    val receipt = state.receipt
    var assigning by remember { mutableStateOf<ItemEntity?>(null) }
    var tipOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = {
                    Text(
                        receipt?.merchant ?: "Receipt",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onPeople) { Icon(Icons.Default.PersonAdd, "People") }
                    IconButton(onClick = { vm.rescan(receiptId) }) {
                        Icon(Icons.Default.Refresh, "Read again")
                    }
                    IconButton(onClick = {
                        receipt?.let { vm.deleteReceipt(it); onBack() }
                    }) { Icon(Icons.Default.Delete, "Delete receipt") }
                },
            )
        },
        bottomBar = {
            Column {
                Rule()
                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).height(64.dp).clickable { tipOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${receipt?.tipPercent ?: DEFAULT_TIP_PERCENT}% TIP",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    Box(
                        Modifier.weight(1f).height(64.dp).clickable(onClick = onTotals),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "TOTALS",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            when {
                receipt == null -> Unit
                receipt.status == ReceiptEntity.STATUS_READING ->
                    EmptyState("Reading the receipt with Claude…")
                receipt.status == ReceiptEntity.STATUS_NO_KEY ->
                    EmptyState("No API key set.\nAdd one in Settings, then tap refresh.")
                receipt.status == ReceiptEntity.STATUS_FAILED ->
                    EmptyState("Couldn't read that photo.\nTap refresh to try again.")
                state.people.isEmpty() ->
                    EmptyState("Add the people at the table first.\nTap the person icon above.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { item ->
                        ItemRow(item, state.peopleOn(item.id)) { assigning = item }
                    }
                }
            }
        }
    }

    assigning?.let { item ->
        AssignDialog(
            item = item,
            people = state.people,
            assignedIds = state.assignments
                .filter { it.itemId == item.id }
                .map { it.personId }
                .toSet(),
            onToggle = { personId, on ->
                vm.toggleAssignment(receiptId, item.id, personId, on)
            },
            onDismiss = { assigning = null },
        )
    }

    if (tipOpen) {
        PercentDialog(
            initial = receipt?.tipPercent ?: DEFAULT_TIP_PERCENT,
            onDismiss = { tipOpen = false },
            onConfirm = { vm.setReceiptTip(receiptId, it); tipOpen = false },
        )
    }
}

@Composable
private fun ItemRow(item: ItemEntity, assigned: List<PersonEntity>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (assigned.isEmpty()) {
                    "unassigned"
                } else {
                    assigned.joinToString("  ") { initialsOf(it.name) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            item.priceCents.asMoney(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun AssignDialog(
    item: ItemEntity,
    people: List<PersonEntity>,
    assignedIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val heads = assignedIds.size
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Column {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (heads > 1) {
                        "${item.priceCents.asMoney()} · ${(item.priceCents / heads).asMoney()} each"
                    } else {
                        item.priceCents.asMoney()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        },
        text = {
            LazyColumn {
                items(people, key = { it.id }) { person ->
                    val checked = person.id in assignedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(person.id, !checked) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            if (checked) {
                                Icons.Default.CheckBox
                            } else {
                                Icons.Default.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            person.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Done", color = Color.White)
            }
        },
    )
}
