@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.model.ScheduledStatus
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.SetDefaultSmsDialog
import com.smsspam.detector.ui.components.defaultSmsRoleIntent
import com.smsspam.detector.ui.components.formatDateTime
import com.smsspam.detector.ui.components.openDefaultAppsSettings
import com.smsspam.detector.ui.components.pickDateTime

/** Lists messages queued for a future send time. */
@Composable
fun ScheduledScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var showDefaultDialog by remember { mutableStateOf(false) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshDefaultSms() }

    LaunchedEffect(Unit) {
        viewModel.refreshContacts()
        viewModel.refreshDefaultSms()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scheduled", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (scheduled.isEmpty()) "Nothing queued" else "${scheduled.size} messages",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (isDefaultSms) showDialog = true else showDefaultDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Schedule") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scheduled.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing scheduled",
                        message = "Queue a message to be sent later — it goes out even if the app is closed, and is restored after a restart.",
                    )
                }
            } else {
                items(scheduled, key = { it.id }) { message ->
                    val displayName = remember(message.recipient, contacts) {
                        viewModel.contactName(message.recipient)
                    }
                    ScheduledCard(
                        message = message,
                        displayName = displayName,
                        onSendNow = { if (isDefaultSms) viewModel.sendScheduledNow(message.id) else showDefaultDialog = true },
                        onCancel = { viewModel.cancelScheduled(message.id) },
                        onDelete = { viewModel.deleteScheduled(message.id) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        NewScheduledDialog(
            onDismiss = { showDialog = false },
            onConfirm = { recipient, body, sendAt ->
                viewModel.scheduleMessage(recipient, body, sendAt)
                showDialog = false
            },
            onPickTime = { current, onPicked -> pickDateTime(context, current, onPicked) },
        )
    }

    if (showDefaultDialog) {
        SetDefaultSmsDialog(
            onDismiss = { showDefaultDialog = false },
            onSetDefault = {
                showDefaultDialog = false
                val intent = defaultSmsRoleIntent(context)
                if (intent != null) roleLauncher.launch(intent) else openDefaultAppsSettings(context)
            },
        )
    }
}

@Composable
private fun ScheduledCard(
    message: ScheduledMessage,
    displayName: String?,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(label = displayName ?: message.recipient.ifBlank { "#" }, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayName ?: message.recipient.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (displayName != null) {
                        Text(
                            text = message.recipient,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = message.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (message.status) {
                        ScheduledStatus.SENT -> MaterialTheme.colorScheme.primary
                        ScheduledStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Send at ${formatDateTime(message.sendAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.status == ScheduledStatus.PENDING) {
                    IconButton(onClick = onSendNow) {
                        Icon(Icons.Filled.Send, contentDescription = "Send now")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun NewScheduledDialog(
    onDismiss: () -> Unit,
    onConfirm: (recipient: String, body: String, sendAt: Long) -> Unit,
    onPickTime: (initial: Long, onPicked: (Long) -> Unit) -> Unit,
) {
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sendAt by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
        title = { Text("Schedule a message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("To (phone number)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onPickTime(sendAt ?: System.currentTimeMillis()) { sendAt = it } },
                ) {
                    Text(sendAt?.let { "Send at ${formatDateTime(it)}" } ?: "Pick date & time")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { sendAt?.let { onConfirm(recipient, body, it) } },
                enabled = recipient.isNotBlank() && body.isNotBlank() && sendAt != null,
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
