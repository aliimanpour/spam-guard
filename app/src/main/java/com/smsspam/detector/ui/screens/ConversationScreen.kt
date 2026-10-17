@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.sms.SmsMessage
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.SetDefaultSmsDialog
import com.smsspam.detector.ui.components.defaultSmsRoleIntent
import com.smsspam.detector.ui.components.formatDateTime
import com.smsspam.detector.ui.components.formatDay
import com.smsspam.detector.ui.components.openDefaultAppsSettings
import com.smsspam.detector.ui.components.pickDateTime
import com.smsspam.detector.ui.components.sameDay

/**
 * A single SMS thread. When [address] is null the screen starts a brand new
 * message with an editable "To" field.
 */
@Composable
fun ConversationScreen(
    viewModel: AppViewModel,
    address: String?,
    initialBody: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val messages by viewModel.conversation.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    var recipient by remember(address) { mutableStateOf(address.orEmpty()) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var showDefaultDialog by remember { mutableStateOf(false) }
    val isNew = address.isNullOrBlank()

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshDefaultSms() }

    LaunchedEffect(address) {
        viewModel.openConversation(address.orEmpty())
        viewModel.refreshContacts()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultSms()
        onPauseOrDispose { }
    }

    // `contacts` is observed so the header updates when contact names arrive.
    val displayTitle = remember(recipient, contacts) { viewModel.contactName(recipient) ?: recipient }
    val canSend = recipient.isNotBlank() && body.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(label = displayTitle.ifBlank { "#" }, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayTitle.ifBlank { "New message" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = if (isNew) "Enter a number to start" else recipient
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            placeholder = { Text("Text message") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (!isDefaultSms) {
                                    showDefaultDialog = true
                                } else {
                                    pickDateTime(context) { at ->
                                        if (viewModel.scheduleMessage(recipient, body, at)) body = ""
                                    }
                                }
                            },
                            enabled = canSend,
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Schedule send")
                        }
                        FilledIconButton(
                            onClick = {
                                if (!isDefaultSms) {
                                    showDefaultDialog = true
                                } else {
                                    viewModel.sendSms(recipient, body)
                                    body = ""
                                }
                            },
                            enabled = canSend,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isNew) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("To (phone number)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (messages.isEmpty()) {
                EmptyState(
                    title = if (isNew) "New message" else "No messages yet",
                    message = if (isNew) {
                        "Type a number and a message, then send or schedule it."
                    } else {
                        "Messages you exchange with this number appear here."
                    },
                )
            } else {
                val ordered = messages.asReversed()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(ordered, key = { _, message -> message.id }) { index, message ->
                        val older = ordered.getOrNull(index + 1)
                        val showDate = older == null || !sameDay(older.timestamp, message.timestamp)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (showDate) DayChip(formatDay(message.timestamp))
                            MessageBubble(message)
                        }
                    }
                }
            }
        }
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
private fun DayChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    val incoming = message.isIncoming
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (incoming) 4.dp else 18.dp,
        bottomEnd = if (incoming) 18.dp else 4.dp,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.82f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
        ) {
            Surface(
                color = if (incoming) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = bubbleShape,
                modifier = Modifier.widthIn(max = maxBubbleWidth),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(message.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatDateTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}
