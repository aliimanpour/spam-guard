@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smsspam.detector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.contacts.ContactsResolver
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.ScanState
import com.smsspam.detector.ui.components.AppPermission
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.HighlightedText
import com.smsspam.detector.ui.components.hasPermission
import com.smsspam.detector.ui.components.relativeTime
import com.smsspam.detector.ui.components.rememberPermissionStatus

private enum class MessageFilter(val label: String, val emptyTitle: String, val emptyMessage: String) {
    BLOCKED(
        label = "Blocked",
        emptyTitle = "No blocked messages",
        emptyMessage = "When a message matches one of your rules it is moved here and removed from the Messages app. Tap “Scan inbox” to check existing messages.",
    ),
    REVIEWED(
        label = "Reviewed",
        emptyTitle = "No reviewed messages",
        emptyMessage = "Messages you restore or mark as not spam appear here.",
    ),
    ALL(
        label = "All",
        emptyTitle = "Nothing here yet",
        emptyMessage = "Messages caught by your rules appear here. Tap “Scan inbox” to check existing messages.",
    ),
}

@Composable
fun QuarantineScreen(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenConversation: (String) -> Unit,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val permissionStatus = rememberPermissionStatus()

    var filter by remember { mutableStateOf(MessageFilter.BLOCKED) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val clearSelection = {
        selecting = false
        selectedIds = emptySet()
    }

    // Leaving selection mode with Back is the expected Android behaviour.
    BackHandler(enabled = selecting) { clearSelection() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus.value = AppPermission.needed.associateWith { context.hasPermission(it.permission) }
        viewModel.refreshSims(force = true)
    }

    // Keep sender names in sync with the contacts the Messages tab resolves.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshContacts()
        onPauseOrDispose { }
    }

    LaunchedEffect(scanState) {
        val state = scanState
        if (state is ScanState.Done) {
            snackbarHostState.showSnackbar(
                "Scanned ${state.scanned} · caught ${state.caught} · removed ${state.removed}"
            )
            viewModel.resetScanState()
        }
    }

    // Opening the tab is "seeing" the blocked messages: the badge clears now,
    // and again on the way out so anything that arrived while it was open does
    // not linger (the app exit resets it as well).
    DisposableEffect(Unit) {
        viewModel.markBlockedSeen()
        onDispose { viewModel.markBlockedSeen() }
    }

    val blocked = messages.filter { it.isBlocked }
    val reviewed = messages.filterNot { it.isBlocked }
    val filtered = when (filter) {
        MessageFilter.BLOCKED -> blocked
        MessageFilter.REVIEWED -> reviewed
        MessageFilter.ALL -> messages
    }
    val visible = remember(filtered, contacts, query) {
        filtered.mapNotNull { message ->
            val name = contacts[ContactsResolver.normalize(message.sender)] ?: message.sender
            val matches = query.isBlank() ||
                name.contains(query, ignoreCase = true) ||
                message.sender.contains(query, ignoreCase = true) ||
                message.body.contains(query, ignoreCase = true)
            if (matches) message to name else null
        }
    }
    val missingPermissions = AppPermission.needed.filter { permissionStatus.value[it] != true }
    val selectedMessages = messages.filter { it.id in selectedIds }

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
                        Text(if (selectedIds.isEmpty()) "Select messages" else "${selectedIds.size} selected")
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.markNotSpam(selectedMessages)
                                clearSelection()
                            },
                            enabled = selectedMessages.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Not spam")
                        }
                        IconButton(
                            onClick = {
                                viewModel.restore(selectedMessages)
                                clearSelection()
                            },
                            enabled = selectedMessages.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = "Restore to Messages")
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selectedMessages.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete permanently")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Blocked", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = when {
                                    visible.isEmpty() -> "No ${filter.label.lowercase()} messages"
                                    visible.size == 1 -> "1 ${filter.label.lowercase()} message"
                                    else -> "${visible.size} ${filter.label.lowercase()} messages"
                                },
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
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Select messages")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Scan inbox for spam") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.scanInbox()
                                },
                            )
                            HorizontalDivider()
                            MessageFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("Show ${option.label.lowercase()}") },
                                    leadingIcon = {
                                        if (filter == option) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        menuOpen = false
                                        filter = option
                                        clearSelection()
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Clear all messages") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.clearAll()
                                },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.scanInbox() },
                    icon = {
                        Icon(
                            if (scanState == ScanState.Scanning) Icons.Filled.Refresh else Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (scanState == ScanState.Scanning) "Scanning…" else "Scan inbox") },
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
                placeholder = { Text("Search blocked messages") },
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

            if (missingPermissions.isNotEmpty()) {
                PermissionBanner(missingPermissions, onRequest = {
                    launcher.launch(missingPermissions.map { it.permission }.toTypedArray())
                })
            }

            if (visible.isEmpty()) {
                EmptyState(
                    title = filter.emptyTitle,
                    message = filter.emptyMessage,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.first.id }) { (message, name) ->
                        BlockedMessageRow(
                            message = message,
                            name = name,
                            query = query,
                            selecting = selecting,
                            selected = message.id in selectedIds,
                            onOpen = { onOpenConversation(message.sender) },
                            onToggleSelect = {
                                selectedIds = if (message.id in selectedIds) {
                                    selectedIds - message.id
                                } else {
                                    selectedIds + message.id
                                }
                            },
                            onLongPress = {
                                selecting = true
                                selectedIds = selectedIds + message.id
                            },
                            onNotSpam = { viewModel.markNotSpam(message) },
                            onRestore = { viewModel.restore(message) },
                            onCopy = { copyText(context, message.body) },
                            onDelete = { viewModel.deleteMessage(message.id) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete messages") },
            text = {
                Text(
                    "Delete ${selectedIds.size} selected " +
                        if (selectedIds.size == 1) "message permanently? This cannot be undone."
                        else "messages permanently? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteMessages(selectedIds)
                    clearSelection()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Copies [text] to the system clipboard. */
private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copied message", text))
}

@Composable
private fun PermissionBanner(missing: List<AppPermission>, onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Setup needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Grant these permissions so Spam Guard can inspect incoming SMS: " +
                    missing.joinToString { it.title } + ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = onRequest,
                label = { Text("Grant permissions") },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
            )
        }
    }
}

/**
 * A blocked message laid out like a row in the Messages tab, with a menu
 * carrying the quarantine-specific actions.
 */
@Composable
private fun BlockedMessageRow(
    message: QuarantineMessage,
    name: String,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onNotSpam: () -> Unit,
    onRestore: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onOpen() },
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
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
                    onCheckedChange = { onToggleSelect() },
                )
                Spacer(Modifier.width(4.dp))
            }
            ContactAvatar(label = name.ifBlank { "Unknown" })
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
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relativeTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = message.body.ifBlank { "(no text)" },
                        query = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
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
                        message.matchedRuleName?.let { rule ->
                            DropdownMenuItem(
                                text = { Text("Rule: $rule", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                enabled = false,
                                onClick = {},
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Not spam") },
                            leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = { menuOpen = false; onNotSpam() },
                        )
                        if (message.status != QuarantineStatus.RESTORED) {
                            DropdownMenuItem(
                                text = { Text("Restore to Messages") },
                                leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                                onClick = { menuOpen = false; onRestore() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy message") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; onCopy() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete permanently") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}
