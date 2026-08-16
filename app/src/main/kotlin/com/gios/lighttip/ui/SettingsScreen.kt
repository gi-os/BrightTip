package com.gios.lighttip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.lighttip.ui.theme.Dim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TipViewModel, onScanQr: () -> Unit, onBack: () -> Unit) {
    val saved by vm.apiKeyState.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Anthropic API key",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-ant-...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.setApiKey(draft) }) { Text("Save key") }
                OutlinedButton(onClick = onScanQr) { Text("Scan QR") }
            }
            Text(
                "Only Receipt mode needs a key — it reads bills with Claude Haiku, roughly a " +
                    "fraction of a cent each. The key is stored on this phone only. Calculator " +
                    "and Tip never touch the network, and Currency works offline on the last " +
                    "rates it downloaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
        }
    }
}
