@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smsspam.detector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.contacts.ContactsResolver
import com.smsspam.detector.model.ConversationSettings
import com.smsspam.detector.sms.SmsConversation
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One row of the conversation list after pin/archive/draft resolution. */
private data class ConversationItem(
    val conversation: SmsConversation,
    val name: String,
    val pinned: Boolean,
    val draft: String,
    val notify: Boolean,
    val sound: Boolean,
    val archived: Boolean,
)

/** Inbox of conversations read from the system SMS provider. */
@Composable
fun ConversationsScreen(
    viewModel: AppViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenContacts: () -> Unit,
    onNewMessage: () -> Unit,
) {
    val context = LocalContext.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val savedMessages by viewModel.savedMessages.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val settings by viewModel.conversationSettings.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    val permissionStatus = rememberPermissionStatus()
    var query by remember { mutableStateOf("") }
    var showDefaultDialog by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedAddresses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showArchived by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Address whose swipe action panel is open, or null.
    var revealedAddress by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingDeletions by viewModel.pendingDeletions.collectAsStateWithLifecycle()

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

    // Resolve display names, drafts and pin/archive state once per data change,
    // not per row or per recomposition. Pinned threads sort to the top.
    val visible = remember(conversations, contacts, query, settings, showArchived, pendingDeletions) {
        conversations.mapNotNull { conversation ->
            if (conversation.address in pendingDeletions) return@mapNotNull null
            val key = ContactsResolver.normalize(conversation.address).ifBlank { conversation.address }
            val state = settings[key] ?: ConversationSettings()
            if (state.archived && !showArchived) return@mapNotNull null
            val name = contacts[ContactsResolver.normalize(conversation.address)] ?: conversation.address
            val matches = query.isBlank() ||
                name.contains(query, ignoreCase = true) ||
                conversation.address.contains(query, ignoreCase = true) ||
                conversation.snippet.contains(query, ignoreCase = true) ||
                state.draft.contains(query, ignoreCase = true)
            if (matches) {
                ConversationItem(conversation, name, state.pinned, state.draft, state.notify, state.sound, state.archived)
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<ConversationItem> { it.pinned }
                .thenByDescending { it.conversation.timestamp }
        )
    }
    val missingRead = !context.hasPermission(AppPermission.READ_SMS.permission)

    // Telegram-style pinned "Saved messages" conversation, shown above the SMS
    // threads unless a search filters it out. Hidden while multi-selecting.
    val savedLatest = savedMessages.lastOrNull()
    val showSaved = !selecting &&
        (query.isBlank() || "saved messages".contains(query, ignoreCase = true))

    // Show (and let the user undo) a pending conversation delete. The row is
    // already hidden; the provider delete only runs when the undo window ends.
    LaunchedEffect(pendingDeletions) {
        if (pendingDeletions.isEmpty()) {
            snackbarHostState.currentSnackbarData?.dismiss()
        } else {
            val result = snackbarHostState.showSnackbar(
                message = if (pendingDeletions.size == 1) {
                    "Conversation deleted"
                } else {
                    "${pendingDeletions.size} conversations deleted"
                },
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            onClick = {
                                viewModel.deleteConversationsWithUndo(selectedAddresses)
                                clearSelection()
                            },
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
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Groups & contacts") },
                                    leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null) },
                                    onClick = { menuOpen = false; onOpenContacts() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showArchived) "Hide archived" else "Show archived") },
                                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                    onClick = { menuOpen = false; showArchived = !showArchived },
                                )
                            }
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

            if (visible.isEmpty() && !showSaved) {
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
                    if (showSaved) {
                        item(key = "saved-messages") {
                            SavedMessagesRow(
                                snippet = savedLatest?.body.orEmpty(),
                                timestamp = savedLatest?.timestamp,
                                onClick = onOpenSaved,
                            )
                        }
                    }
                    items(visible, key = { it.conversation.address }) { item ->
                        val conversation = item.conversation
                        val address = conversation.address
                        // Warm the cache while the user scrolls: every row that
                        // becomes visible queues its newest page for prefetch,
                        // so opening it later paints instantly.
                        LaunchedEffect(address) {
                            viewModel.prefetchConversation(conversation)
                        }
                        SwipeRevealRow(
                            revealed = revealedAddress == address,
                            onRevealedChange = { open ->
                                revealedAddress = if (open) address else null
                            },
                            actions = {
                                ConversationSwipeActions(
                                    archived = item.archived,
                                    unread = conversation.unreadCount > 0,
                                    onArchive = {
                                        revealedAddress = null
                                        viewModel.setConversationArchived(address, !item.archived)
                                    },
                                    onToggleRead = {
                                        revealedAddress = null
                                        if (conversation.unreadCount > 0) {
                                            viewModel.markConversationRead(address)
                                        } else {
                                            viewModel.markConversationUnread(address)
                                        }
                                    },
                                    onDelete = {
                                        revealedAddress = null
                                        viewModel.deleteConversationsWithUndo(setOf(address))
                                    },
                                )
                            },
                        ) {
                            ConversationRow(
                                name = item.name,
                                address = address,
                                snippet = conversation.snippet,
                                draft = item.draft,
                                pinned = item.pinned,
                                notifyEnabled = item.notify,
                                soundEnabled = item.sound,
                                timestamp = conversation.timestamp,
                                unread = conversation.unreadCount,
                                query = query,
                                selecting = selecting,
                                selected = address in selectedAddresses,
                                onClick = {
                                    if (revealedAddress == address) {
                                        revealedAddress = null
                                    } else if (selecting) {
                                        selectedAddresses = if (address in selectedAddresses) {
                                            selectedAddresses - address
                                        } else {
                                            selectedAddresses + address
                                        }
                                    } else {
                                        onOpenConversation(address)
                                    }
                                },
                                onLongPress = {
                                    selecting = true
                                    selectedAddresses = selectedAddresses + address
                                },
                                onDelete = {
                                    revealedAddress = null
                                    viewModel.deleteConversationsWithUndo(setOf(address))
                                },
                                onMarkUnread = {
                                    if (conversation.unreadCount > 0) {
                                        viewModel.markConversationRead(address)
                                    } else {
                                        viewModel.markConversationUnread(address)
                                    }
                                },
                                onCall = { dialNumber(context, address) },
                                onCopyNumber = { copyText(context, address) },
                                onPin = { viewModel.setConversationPinned(address, !item.pinned) },
                                onArchive = { viewModel.setConversationArchived(address, !item.archived) },
                                onToggleNotify = { viewModel.setConversationNotify(address, !item.notify) },
                                onToggleSound = { viewModel.setConversationSound(address, !item.sound) },
                            )
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

/** Reveals a horizontal action panel when [content] is swiped left. */
@Composable
private fun SwipeRevealRow(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { ACTION_PANEL_WIDTH.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(revealed, revealPx) {
        offset.animateTo(if (revealed) -revealPx else 0f)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Actions sit behind the row and are exposed as it slides left; they are
        // only composed while the panel is (partly) open so the row's rounded
        // corners never let them peek through at rest.
        if (revealed || offset.value < -0.5f) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
                content = actions,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = { onRevealedChange(offset.value < -revealPx / 3f) },
                ),
        ) {
            content()
        }
    }
}

/** The three swipe actions behind a conversation row. */
@Composable
private fun ConversationSwipeActions(
    archived: Boolean,
    unread: Boolean,
    onArchive: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeAction(
        icon = Icons.Filled.Archive,
        label = if (archived) "Unarchive" else "Archive",
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onArchive,
    )
    SwipeAction(
        icon = if (unread) Icons.Filled.Done else Icons.Filled.MarkEmailUnread,
        label = if (unread) "Mark read" else "Unread",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onToggleRead,
    )
    SwipeAction(
        icon = Icons.Filled.Delete,
        label = "Delete",
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onDelete,
    )
}

@Composable
private fun SwipeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(ACTION_WIDTH)
            .fillMaxHeight()
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/** Swipe panel geometry: three [ACTION_WIDTH] actions wide. */
private val ACTION_WIDTH = 64.dp
private val ACTION_PANEL_WIDTH = ACTION_WIDTH * 3

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

/** Pinned "Saved messages" row at the top of the conversation list. */
@Composable
private fun SavedMessagesRow(
    snippet: String,
    timestamp: Long?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Saved messages",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snippet.ifBlank { "Notes to yourself" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (timestamp != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    name: String,
    address: String,
    snippet: String,
    draft: String,
    pinned: Boolean,
    notifyEnabled: Boolean,
    soundEnabled: Boolean,
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
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onToggleNotify: () -> Unit,
    onToggleSound: () -> Unit,
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
                    if (pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
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
                    if (!notifyEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = "Notifications muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (!soundEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.VolumeOff,
                            contentDescription = "Sound silenced",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relativeTime(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = if (draft.isNotBlank()) "Draft: $draft" else snippet.ifBlank { "(no text)" },
                        query = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (draft.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else if (unread > 0) {
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
                            text = { Text(if (unread > 0) "Mark as read" else "Mark as unread") },
                            leadingIcon = {
                                Icon(
                                    if (unread > 0) Icons.Filled.Done else Icons.Filled.MarkEmailUnread,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onMarkUnread() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy number") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; onCopyNumber() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (pinned) "Unpin" else "Pin") },
                            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; onPin() },
                        )
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            onClick = { menuOpen = false; onArchive() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (notifyEnabled) "Mute notifications" else "Unmute notifications") },
                            leadingIcon = {
                                Icon(
                                    if (notifyEnabled) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onToggleNotify() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (soundEnabled) "Silence sound" else "Enable sound") },
                            leadingIcon = {
                                Icon(
                                    if (soundEnabled) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onToggleSound() },
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
