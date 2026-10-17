@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsspam.detector.model.AutoReply
import com.smsspam.detector.model.ReplyTrigger
import com.smsspam.detector.ui.AppViewModel

/** Create or edit one keyword auto-reply. */
@Composable
fun AutoReplyEditorScreen(
    viewModel: AppViewModel,
    replyId: String?,
    onDone: () -> Unit,
) {
    val existing = remember(replyId) { replyId?.let { id -> viewModel.autoReplies.value.firstOrNull { it.id == id } } }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var trigger by remember { mutableStateOf(existing?.trigger ?: ReplyTrigger.CONTAINS) }
    var pattern by remember { mutableStateOf(existing?.pattern.orEmpty()) }
    var response by remember { mutableStateOf(existing?.response.orEmpty()) }
    var senderFilter by remember { mutableStateOf(existing?.senderFilter.orEmpty()) }
    var oncePerSender by remember { mutableStateOf(existing?.oncePerSender ?: true) }
    var triggerMenu by remember { mutableStateOf(false) }

    val canSave = response.isNotBlank() &&
        (trigger == ReplyTrigger.ANY || pattern.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New auto-reply" else "Edit auto-reply") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Out of office") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When a message…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { triggerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(trigger.label, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = triggerMenu, onDismissRequest = { triggerMenu = false }) {
                        ReplyTrigger.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { trigger = option; triggerMenu = false },
                            )
                        }
                    }
                }
                if (trigger != ReplyTrigger.ANY) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text(if (trigger == ReplyTrigger.REGEX) "Regex pattern" else "Keyword or text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = response,
                onValueChange = { response = it },
                label = { Text("Reply message") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = senderFilter,
                onValueChange = { senderFilter = it },
                label = { Text("Only from number (optional)") },
                placeholder = { Text("Leave blank to reply to anyone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reply once per sender", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Prevents an endless reply loop with the same contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = oncePerSender, onCheckedChange = { oncePerSender = it })
                }
            }

            Button(
                onClick = {
                    val reply = (existing ?: AutoReply()).copy(
                        name = name.trim(),
                        trigger = trigger,
                        pattern = pattern.trim(),
                        response = response.trim(),
                        senderFilter = senderFilter.trim(),
                        oncePerSender = oncePerSender,
                    )
                    viewModel.saveAutoReply(reply)
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
