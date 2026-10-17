@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTarget
import com.smsspam.detector.model.WorkflowTrigger
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.formatDateTime

/** Lists the automated workflows. */
@Composable
fun WorkflowsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEditWorkflow: (String) -> Unit,
) {
    val workflows by viewModel.workflows.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Workflow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditWorkflow("new") }) {
                Icon(Icons.Filled.Add, contentDescription = "New workflow")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Workflows send automatically: greet new contacts, answer a keyword, fire " +
                        "a timed reminder, or nudge someone who has not replied.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (followUps.isNotEmpty()) {
                item {
                    Text(
                        "${followUps.size} follow-up${if (followUps.size == 1) "" else "s"} waiting " +
                            "for a reply",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (workflows.isEmpty()) {
                item {
                    Text(
                        "No workflows yet. Add one with the + button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(workflows, key = { it.id }) { workflow ->
                    WorkflowCard(
                        workflow = workflow,
                        onToggle = { viewModel.setWorkflowEnabled(workflow.id, it) },
                        onClick = { onEditWorkflow(workflow.id) },
                        onDelete = { pendingDelete = workflow },
                    )
                }
            }
        }
    }

    pendingDelete?.let { workflow ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete workflow?") },
            text = { Text("“${workflow.name.ifBlank { "Untitled" }}” will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkflow(workflow.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkflowCard(
    workflow: Workflow,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = workflow.name.ifBlank { "Untitled workflow" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = triggerSummary(workflow),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "→ ${workflow.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (workflow.trigger == WorkflowTrigger.REMINDER &&
                    workflow.target != WorkflowTarget.SENDER
                ) {
                    AssistChip(onClick = { }, label = { Text("To ${workflow.target.label}") })
                }
            }
            Switch(checked = workflow.enabled, onCheckedChange = onToggle)
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Workflow options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

private fun triggerSummary(workflow: Workflow): String = when (workflow.trigger) {
    WorkflowTrigger.WELCOME -> "First message from a contact"
    WorkflowTrigger.KEYWORD -> "Message contains “${workflow.keyword}”"
    WorkflowTrigger.REMINDER -> if (workflow.triggerAt > 0L) {
        "At ${formatDateTime(workflow.triggerAt)}"
    } else {
        "Reminder at a time"
    }
    WorkflowTrigger.FOLLOW_UP -> "No reply within ${workflow.followUpDelayMinutes} min"
}
