@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smsspam.detector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.contacts.Recipient
import com.smsspam.detector.contacts.RecipientDirectory
import com.smsspam.detector.sms.SendStatus
import com.smsspam.detector.sms.SimSubscription
import com.smsspam.detector.sms.SmsMessage
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.OlderState
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.HighlightedText
import com.smsspam.detector.ui.components.SetDefaultSmsDialog
import com.smsspam.detector.ui.components.defaultSmsRoleIntent
import com.smsspam.detector.ui.components.formatDay
import com.smsspam.detector.ui.components.formatTime
import com.smsspam.detector.ui.components.isRtl
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
    onForward: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val messages by viewModel.conversation.collectAsStateWithLifecycle()
    val olderState by viewModel.olderState.collectAsStateWithLifecycle()
    val threadLoading by viewModel.threadLoading.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    val sims by viewModel.sims.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var recipient by remember(address) { mutableStateOf(address.orEmpty()) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var showDefaultDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Message whose long-press menu is open, or null.
    var menuMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var showConversationMenu by remember { mutableStateOf(false) }
    // User's SIM choice for this open; null keeps the remembered per-conversation SIM.
    var simOverride by remember(address) { mutableStateOf<Int?>(null) }
    // Set right after a send so the next list change scrolls back to the new
    // balloon even if the user had scrolled up into history.
    var jumpToLatest by remember { mutableStateOf(false) }
    val chosenSubId = simOverride ?: viewModel.selectedSubId(address.orEmpty())
    val isNew = address.isNullOrBlank()

    // Suggestions for the editable "To" field: contacts plus any number that has
    // an SMS thread or a call-log entry. Hidden once the field matches a pick.
    val suggestions = remember(recipients, recipient, isNew) {
        if (isNew) {
            RecipientDirectory.search(recipients, recipient).filter { it.number != recipient }
        } else {
            emptyList()
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshDefaultSms() }

    LaunchedEffect(address) {
        viewModel.openConversation(address.orEmpty())
        viewModel.refreshContacts()
        viewModel.refreshRecipients()
        viewModel.refreshSims()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultSms()
        viewModel.refreshRecipients()
        viewModel.refreshSims()
        onPauseOrDispose { }
    }

    // `contacts` is observed so the header updates when contact names arrive.
    val displayTitle = remember(recipient, contacts) { viewModel.contactName(recipient) ?: recipient }
    val canSend = recipient.isNotBlank() && body.isNotBlank()

    // Sends the current draft (or asks for the default-SMS role first) and
    // flags the thread to scroll to the newly created balloon.
    val sendMessage: () -> Unit = {
        if (!isDefaultSms) {
            showDefaultDialog = true
        } else if (canSend) {
            viewModel.sendSms(recipient, body, chosenSubId)
            body = ""
            jumpToLatest = true
        }
    }

    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
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
                actions = {
                    if (!isNew) {
                        Box {
                            IconButton(onClick = { showConversationMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showConversationMenu,
                                onDismissRequest = { showConversationMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Call") },
                                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                    onClick = {
                                        showConversationMenu = false
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$recipient"))
                                            )
                                        } catch (_: Exception) { }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy number") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showConversationMenu = false
                                        copyText(recipient)
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete conversation") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        showConversationMenu = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                // Lifts the composer above the gesture-navigation area when the
                // keyboard is closed; the Scaffold's imePadding consumes the
                // same insets while the keyboard is open, so no double padding.
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MessageInputField(
                            value = body,
                            onValueChange = { body = it },
                            onSend = { sendMessage() },
                            modifier = Modifier.weight(1f),
                        )
                        if (sims.size > 1) {
                            SimPicker(
                                sims = sims,
                                selectedSubId = chosenSubId,
                                onSelect = { sub ->
                                    simOverride = sub
                                    address?.let { viewModel.setSim(it, sub) }
                                },
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!isDefaultSms) {
                                    showDefaultDialog = true
                                } else {
                                    pickDateTime(context) { at ->
                                        if (viewModel.scheduleMessage(recipient, body, at, chosenSubId)) body = ""
                                    }
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "Schedule send",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        FilledIconButton(
                            onClick = sendMessage,
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp),
                            )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("To (name or number)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (suggestions.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            suggestions.forEach { suggestion ->
                                RecipientSuggestionRow(
                                    suggestion = suggestion,
                                    query = recipient,
                                    onClick = { recipient = suggestion.number },
                                )
                            }
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                // A thread the cache has never seen shows progress instead of
                // "No messages yet" so the first load never looks empty.
                if (!isNew && threadLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    EmptyState(
                        title = if (isNew) "New message" else "No messages yet",
                        message = if (isNew) {
                            "Type a contact name or number and a message, then send or schedule it."
                        } else {
                            "Messages you exchange with this number appear here."
                        },
                    )
                }
            } else {
                val ordered = messages.asReversed()
                // Keep the view pinned to the newest message while it is already
                // at the bottom, so arriving messages flow in without dragging
                // the list away from history the user is reading. A send always
                // scrolls back to the newest balloon instead.
                LaunchedEffect(ordered.firstOrNull()?.id) {
                    when {
                        jumpToLatest -> {
                            jumpToLatest = false
                            listState.animateScrollToItem(0)
                        }
                        listState.firstVisibleItemIndex == 0 -> listState.scrollToItem(0)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
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
                            MessageBubble(
                                message = message,
                                onCopy = { copyText(message.body) },
                                onForward = { onForward(message.body) },
                                onShare = { shareText(context, message.body) },
                                onRetry = {
                                    viewModel.retrySend(message.id, message.address, message.body, chosenSubId)
                                },
                                onDelete = { viewModel.deleteMessage(message) },
                                onMenuDismiss = { menuMessage = null },
                                menuOpen = menuMessage?.id == message.id,
                                onLongClick = { menuMessage = message },
                            )
                        }
                    }
                    // With reverseLayout, items declared after the messages sit
                    // at the visually top (older) end of the list.
                    when (olderState) {
                        OlderState.Loading -> item(key = "older_loading") {
                            ThreadStatusChip("Loading older messages…")
                        }
                        // Composing this item is the scroll trigger: it only
                        // exists when the end of the loaded history is reached,
                        // and re-arms per loaded page until the thread ends.
                        OlderState.Ready -> item(key = "older_loader") {
                            LoadOlderTrigger(loadedCount = ordered.size) {
                                viewModel.loadOlderMessages()
                            }
                        }
                        OlderState.EndReached -> item(key = "older_end") {
                            ThreadStatusChip("Beginning of conversation")
                        }
                        OlderState.Idle -> Unit
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete conversation") },
            text = {
                Text("Delete all messages in this conversation? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteConversation(recipient)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Shares [text] via the Android share sheet (best effort). */
private fun shareText(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (_: Exception) {
        // No app to handle the share intent.
    }
}

/**
 * Pill-shaped message input for the compose bar. Stays one compact line while
 * empty and grows up to four lines as the draft grows; the keyboard's IME
 * send action triggers [onSend].
 */
@Composable
private fun MessageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Text message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Compact SIM chooser for the compose bar: shows the outgoing SIM's slot as a
 * badge and lists every active SIM in a dropdown. Only rendered on dual-SIM
 * devices; the choice is remembered per conversation by the ViewModel.
 */
@Composable
private fun SimPicker(
    sims: List<SimSubscription>,
    selectedSubId: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = sims.firstOrNull { it.subId == selectedSubId } ?: sims.firstOrNull()
    Box {
        FilledTonalIconButton(onClick = { open = true }) {
            BadgedBox(badge = {
                Badge { Text("${selected?.slot ?: 1}") }
            }) {
                Icon(
                    Icons.Filled.SimCard,
                    contentDescription = "SIM: ${selected?.displayName ?: "default"}",
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text("SIM ${sim.slot} · ${sim.displayName}") },
                    trailingIcon = if (sim.subId == selectedSubId) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(sim.subId)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipientSuggestionRow(
    suggestion: Recipient,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContactAvatar(label = suggestion.label, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            HighlightedText(
                text = suggestion.label,
                query = query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.name != null && suggestion.number != suggestion.name) {
                HighlightedText(
                    text = suggestion.number,
                    query = query,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

/** Small centered status chip shown at the older end of a thread. */
@Composable
private fun ThreadStatusChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
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
}

/**
 * Invisible pagination sentinel at the older end of the thread. Entering
 * composition means the user reached the loaded history boundary and requests
 * the next page; keying on [loadedCount] re-fires it while the sentinel stays
 * visible, so holding the scroll at the end keeps older pages streaming in.
 */
@Composable
private fun LoadOlderTrigger(loadedCount: Int, onLoad: () -> Unit) {
    LaunchedEffect(loadedCount) { onLoad() }
    Box(Modifier.fillMaxWidth())
}

/**
 * A single message bubble with a tail, sending-status icon, and a long-press
 * menu (copy, forward, share, delete, retry on failure).
 */
@Composable
private fun MessageBubble(
    message: SmsMessage,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMenuDismiss: () -> Unit,
    menuOpen: Boolean,
    onLongClick: () -> Unit,
) {
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
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick,
                    ),
            ) {
                // Lay the bubble's contents out in the text's own direction so
                // Persian/Arabic messages read right-to-left (and the timestamp
                // mirrors to the opposite corner) while the bubble itself still
                // sits on the incoming/outgoing side of the thread.
                val contentDirection =
                    if (isRtl(message.body)) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (incoming) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = formatTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (incoming) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                },
                            )
                            if (!incoming) {
                                SendStatusIcon(message.status, onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = onMenuDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onMenuDismiss(); onCopy() },
        )
        DropdownMenuItem(
            text = { Text("Forward") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
            onClick = { onMenuDismiss(); onForward() },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onMenuDismiss(); onShare() },
        )
        if (message.status == SendStatus.FAILED) {
            DropdownMenuItem(
                text = { Text("Retry") },
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                onClick = { onMenuDismiss(); onRetry() },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = { onMenuDismiss(); onDelete() },
        )
    }
}

/** Small status icon for outgoing messages: clock (sending), check (sent), error (failed). */
@Composable
private fun SendStatusIcon(status: SendStatus, onRetry: () -> Unit) {
    when (status) {
        SendStatus.SENDING -> Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = "Sending",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        SendStatus.SENT -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Sent",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        SendStatus.FAILED -> Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Failed",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
