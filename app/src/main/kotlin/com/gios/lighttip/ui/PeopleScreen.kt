package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gios.lighttip.hw.WheelScroll
import com.gios.lighttip.ui.theme.Dim

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(vm: TipViewModel, receiptId: String, onBack: () -> Unit) {
    val flow = remember(receiptId) { vm.receiptState(receiptId) }
    val state by flow.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    // Names carry over between bills, so the regular crowd is one tap rather than typing.
    val suggestions = remember { vm.recentNames() }
    // The name field is focused most of the time here, which is exactly why the
    // activity intercepts the wheel before the view hierarchy sees it.
    val listState = rememberLazyListState()
    WheelScroll(listState)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("People") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            vm.addPerson(receiptId, draft)
                            draft = ""
                        }
                    },
                ) { Text("Add") }
            }

            val unused = suggestions.filterNot { name ->
                state.people.any { it.name.equals(name, ignoreCase = true) }
            }
            if (unused.isNotEmpty()) {
                Text(
                    "RECENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    unused.forEach { name ->
                        TextButton(onClick = { vm.addPerson(receiptId, name) }) {
                            Text("+ $name", color = Color.White)
                        }
                    }
                }
            }
            Rule()

            if (state.people.isEmpty()) {
                EmptyState("Nobody added yet.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(state.people, key = { it.id }) { person ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                person.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.deletePerson(person) }) {
                                Icon(Icons.Default.Close, "Remove ${person.name}", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
