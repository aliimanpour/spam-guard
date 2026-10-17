@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smsspam.detector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.contacts.ContactsResolver
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.AppPermission
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.HighlightedText
import com.smsspam.detector.ui.components.SetDefaultSmsDialog
import com.smsspam.detector.ui.components.defaultSmsRoleIntent
import com.smsspam.detector.ui.components.hasPermission
import com.smsspam.detector.ui.components.openDefaultAppsSettings
import com.smsspam.detector.ui.components.relativeTime
import com.smsspam.detector.ui.components.rememberPermissionStatus

/** Inbox of conversations read from the system SMS provider. */
@Composable
fun ConversationsScreen(
    viewModel: AppViewModel,
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
) {
    val context = LocalContext.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    val permissionStatus = rememberPermissionStatus()
    var query by remember { mutableStateOf("") }
    var showDefaultDialog by remember { mutableStateOf(false) }
    var deleteAddress by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedAddresses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteSelectedDialog by remember { mutableStateOf(false) }

    val clearSelection = {
        selecting = false
        selectedAddresses = emptySet()
    }

    BackHandler(enabled = selecting) { clearSelection() }

val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus.value = AppPermission.needed.associateWith { context.hasPermission(it.permission) }
        // Force: the freshness window must not swallow the first load after
        // the permission (or its rejection) changed what we can read.
        viewModel.refreshConversations(force = true)
        viewModel.refreshContacts(force = true)
        viewModel.refreshSims(force = true)
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshDefaultSms() }

    // Single load path: this runs on first composition and every time the
    // destination resumes. The ViewModel coalesces in-flight reads, so there is
    // no duplicate inbox/contacts work when returning to this screen.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultSms()
        viewModel.refreshConversations()
        viewModel.refreshContacts()
        viewModel.refreshSims()
        onPauseOrDispose { }
    }

    // Resolve display names and filter once per data change, not per row or
    // per recomposition.
    val visible = remember(conversations, contacts, query) {
        conversations.mapNotNull { conversation ->
            val name = contacts[ContactsResolver.normalize(conversation.address)] ?: conversation.address
            val matches = query.isBlank() ||
                name.contains(query, ignoreCase = true) ||
                conversation.address.contains(query, ignoreCase = true) ||
                conversation.snippet.contains(query, ignoreCase = true)
            if (matches) conversation to name else null
        }
    }
    val missingRead = !context.hasPermission(AppPermission.READ_SMS.permission)

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    title = {
                        Text(if (selectedAddresses.isEmpty()) "Select conversations" else "${selectedAddresses.size} selected")
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.markConversationsUnread(selectedAddresses)
                                clearSelection()
                            },
                            enabled = selectedAddresses.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.MarkEmailUnread, contentDescription = "Mark as unread")
                        }
                        IconButton(
                            onClick = { deleteSelectedDialog = true },
                            enabled = selectedAddresses.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete conversations")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Messages", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = if (visible.isEmpty()) "No conversations" else "${visible.size} conversations",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        IconButton(onClick = {
                            selecting = true
                            selectedAddresses = emptySet()
                        }) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Select conversations")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessage,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    text = { Text("New message") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search conversations") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (missingRead) {
                CallToActionCard(
                    title = "Read your messages",
                    message = "Grant Read messages so Spam Guard can show your conversations.",
                    actionLabel = "Grant permission",
                    onAction = { permissionLauncher.launch(arrayOf(AppPermission.READ_SMS.permission)) },
                )
            } else if (!isDefaultSms) {
                CallToActionCard(
                    title = "Not your default SMS app",
                    message = "Set Spam Guard as your default SMS app to send messages and save them to your inbox.",
                    actionLabel = "Set as default",
                    onAction = { showDefaultDialog = true },
                )
            }

            if (visible.isEmpty()) {
                EmptyState(
                    title = if (missingRead) "Messages are hidden" else "No conversations yet",
                    message = if (missingRead) {
                        "Grant the Read messages permission to see and reply to your SMS here."
                    } else {
                        "Start a new message with the button below, or wait for an SMS to arrive."
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.first.address }) { (conversation, name) ->
                        // Warm the cache while the user scrolls: every row that
                        // becomes visible queues its newest page for prefetch,
                        // so opening it later paints instantly.
                        LaunchedEffect(conversation.address) {
                            viewModel.prefetchConversation(conversation)
                        }
                        ConversationRow(
                            name = name,
                            address = conversation.address,
                            snippet = conversation.snippet,
                            timestamp = conversation.timestamp,
                            unread = conversation.unreadCount,
                            query = query,
                            selecting = selecting,
                            selected = conversation.address in selectedAddresses,
                            onClick = {
                                if (selecting) {
                                    selectedAddresses = if (conversation.address in selectedAddresses) {
                                        selectedAddresses - conversation.address
                                    } else {
                                        selectedAddresses + conversation.address
                                    }
                                } else {
                                    onOpenConversation(conversation.address)
                                }
                            },
                            onLongPress = {
                                selecting = true
                                selectedAddresses = selectedAddresses + conversation.address
                            },
                            onDelete = { deleteAddress = conversation.address },
                            onMarkUnread = { viewModel.markConversationUnread(conversation.address) },
                            onCall = { dialNumber(context, conversation.address) },
                            onCopyNumber = { copyText(context, conversation.address) },
                        )
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

    deleteAddress?.let { address ->
        AlertDialog(
            onDismissRequest = { deleteAddress = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete conversation") },
            text = { Text("Delete all messages from $address? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteAddress = null
                    viewModel.deleteConversation(address)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteAddress = null }) { Text("Cancel") }
            },
        )
    }

    if (deleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { deleteSelectedDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete conversations") },
            text = {
                Text(
                    "Delete ${selectedAddresses.size} selected " +
                        if (selectedAddresses.size == 1) {
                            "conversation? This cannot be undone."
                        } else {
                            "conversations? This cannot be undone."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelectedDialog = false
                    viewModel.deleteConversations(selectedAddresses)
                    clearSelection()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Opens the system dialer with [number] pre-filled (best effort). */
private fun dialNumber(context: Context, number: String) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
        )
    } catch (_: Exception) {
        // No dialer app available.
    }
}

/** Copies [text] to the system clipboard. */
private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copied text", text))
}

@Composable
private fun ConversationRow(
    name: String,
    address: String,
    snippet: String,
    timestamp: Long,
    unread: Int,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onMarkUnread: () -> Unit,
    onCall: () -> Unit,
    onCopyNumber: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Unread conversations get a slightly stronger container so the eye picks
    // them out before the badge is even noticed.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                unread > 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
                Spacer(Modifier.width(4.dp))
            }
            ContactAvatar(label = name)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = name.ifBlank { "Unknown" },
                        query = query,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relativeTime(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = snippet.ifBlank { "(no text)" },
                        query = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(unread)
                    }
                }
            }
            if (!selecting) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Call") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                            onClick = { menuOpen = false; onCall() },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as unread") },
                            onClick = { menuOpen = false; onMarkUnread() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy number") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; onCopyNumber() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CallToActionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
