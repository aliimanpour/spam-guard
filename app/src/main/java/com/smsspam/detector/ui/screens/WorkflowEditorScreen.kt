@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTarget
import com.smsspam.detector.model.WorkflowTrigger
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.formatDateTime
import com.smsspam.detector.ui.components.pickDateTime

/** Create or edit one automated workflow. */
@Composable
fun WorkflowEditorScreen(
    viewModel: AppViewModel,
    workflowId: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val existing = remember(workflowId) {
        workflowId?.let { id -> viewModel.workflows.value.firstOrNull { it.id == id } }
    }
    val tags = remember { viewModel.allTags() }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var trigger by remember { mutableStateOf(existing?.trigger ?: WorkflowTrigger.WELCOME) }
    var keyword by remember { mutableStateOf(existing?.keyword.orEmpty()) }
    var triggerAt by remember { mutableStateOf(existing?.triggerAt ?: 0L) }
    var delayMinutes by remember { mutableStateOf((existing?.followUpDelayMinutes ?: 60).toString()) }
    var target by remember { mutableStateOf(existing?.target ?: WorkflowTarget.SENDER) }
    var targetValue by remember { mutableStateOf(existing?.targetValue.orEmpty()) }
    var message by remember { mutableStateOf(existing?.message.orEmpty()) }
    var triggerMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }

    val effectiveTarget = if (trigger == WorkflowTrigger.REMINDER) target else WorkflowTarget.SENDER
    val canSave = message.isNotBlank() && when (trigger) {
        WorkflowTrigger.KEYWORD -> keyword.isNotBlank()
        WorkflowTrigger.REMINDER -> triggerAt > 0L && when (effectiveTarget) {
            WorkflowTarget.NUMBERS -> targetValue.isNotBlank()
            WorkflowTarget.GROUP, WorkflowTarget.TAG -> targetValue.isNotBlank()
            WorkflowTarget.SENDER -> false
        }
        else -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New workflow" else "Edit workflow") },
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
                placeholder = { Text("e.g. Welcome new leads") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trigger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { triggerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(trigger.label, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = triggerMenu, onDismissRequest = { triggerMenu = false }) {
                        WorkflowTrigger.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { trigger = option; triggerMenu = false },
                            )
                        }
                    }
                }
            }

            when (trigger) {
                WorkflowTrigger.KEYWORD -> {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WorkflowTrigger.REMINDER -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Send at", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(
                            onClick = {
                                pickDateTime(context, triggerAt.takeIf { it > 0 } ?: System.currentTimeMillis()) {
                                    triggerAt = it
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                            Text(
                                if (triggerAt > 0L) "  ${formatDateTime(triggerAt)}" else "  Choose date & time",
                            )
                        }
                        Text("Send to", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Box {
                            OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(target.label, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                                listOf(WorkflowTarget.NUMBERS, WorkflowTarget.GROUP, WorkflowTarget.TAG).forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { target = option; targetValue = ""; targetMenu = false },
                                    )
                                }
                            }
                        }
                        when (target) {
                            WorkflowTarget.NUMBERS -> OutlinedTextField(
                                value = targetValue,
                                onValueChange = { targetValue = it },
                                label = { Text("Numbers (comma separated)") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            WorkflowTarget.GROUP -> GroupTagPicker(
                                label = "Group",
                                options = groups.map { it.id to it.name },
                                selected = targetValue,
                                onSelect = { targetValue = it },
                            )
                            WorkflowTarget.TAG -> GroupTagPicker(
                                label = "Tag",
                                options = tags.map { it to it },
                                selected = targetValue,
                                onSelect = { targetValue = it },
                            )
                            WorkflowTarget.SENDER -> Unit
                        }
                    }
                }
                WorkflowTrigger.FOLLOW_UP -> {
                    OutlinedTextField(
                        value = delayMinutes,
                        onValueChange = { delayMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Send follow-up after (minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WorkflowTrigger.WELCOME -> Unit
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val workflow = (existing ?: Workflow()).copy(
                        name = name.trim(),
                        trigger = trigger,
                        keyword = keyword.trim(),
                        triggerAt = triggerAt,
                        followUpDelayMinutes = delayMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 60,
                        target = effectiveTarget,
                        targetValue = if (trigger == WorkflowTrigger.REMINDER) targetValue.trim() else "",
                        message = message.trim(),
                    )
                    viewModel.saveWorkflow(workflow)
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

@Composable
private fun GroupTagPicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel ?: "Choose $label", modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("Nothing available") }, enabled = false, onClick = {})
            }
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); open = false },
                )
            }
        }
    }
}
