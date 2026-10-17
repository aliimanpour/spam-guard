@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.ScanState
import com.smsspam.detector.ui.components.AppPermission
import com.smsspam.detector.ui.components.EmptyState
import com.smsspam.detector.ui.components.hasPermission
import com.smsspam.detector.ui.components.isRtl
import com.smsspam.detector.ui.components.relativeTime
import com.smsspam.detector.ui.components.rememberPermissionStatus

private enum class MessageFilter(val label: String) {
    BLOCKED("Blocked"),
    REVIEWED("Reviewed"),
    ALL("All"),
}

@Composable
fun QuarantineScreen(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val permissionStatus = rememberPermissionStatus()

    var filter by remember { mutableStateOf(MessageFilter.BLOCKED) }
    var menuOpen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus.value = AppPermission.needed.associateWith { context.hasPermission(it.permission) }
        viewModel.refreshSims(force = true)
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
    val visible = when (filter) {
        MessageFilter.BLOCKED -> blocked
        MessageFilter.REVIEWED -> reviewed
        MessageFilter.ALL -> messages
    }
    val missingPermissions = AppPermission.needed.filter { permissionStatus.value[it] != true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked messages") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    IconButton(onClick = { viewModel.scanInbox() }, enabled = scanState != ScanState.Scanning) {
                        Icon(Icons.Filled.Search, contentDescription = "Scan inbox")
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
                        DropdownMenuItem(
                            text = { Text("Clear reviewed messages") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.clearAll()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
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
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missingPermissions.isNotEmpty()) {
                item { PermissionBanner(missingPermissions, onRequest = {
                    launcher.launch(missingPermissions.map { it.permission }.toTypedArray())
                }) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = {
                                val count = when (option) {
                                    MessageFilter.BLOCKED -> blocked.size
                                    MessageFilter.REVIEWED -> reviewed.size
                                    MessageFilter.ALL -> messages.size
                                }
                                Text("${option.label} ($count)")
                            },
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "When a message matches one of your rules it is moved here and removed from the Messages app. Tap “Scan inbox” to check existing messages.",
                    )
                }
            } else {
                items(visible, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        onNotSpam = { viewModel.markNotSpam(message) },
                        onRestore = { viewModel.restore(message) },
                        onDelete = { viewModel.deleteMessage(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(missing: List<AppPermission>, onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun MessageCard(
    message: QuarantineMessage,
    onNotSpam: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.sender.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = relativeTime(message.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            message.matchedRuleName?.let { rule ->
                AssistChip(
                    onClick = { },
                    label = { Text(rule, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }

            // Lay the body out in the text's own direction so Persian/Arabic
            // messages read right-to-left and align to the right edge,
            // mirroring the conversation bubbles.
            val bodyDirection =
                if (isRtl(message.body)) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides bodyDirection) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusLabel = when (message.status) {
                    QuarantineStatus.RESTORED -> "Restored"
                    QuarantineStatus.NOT_SPAM -> "Not spam"
                    QuarantineStatus.CONFIRMED_SPAM -> "Confirmed"
                    QuarantineStatus.SPAM -> null
                }
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onNotSpam) {
                    Icon(Icons.Filled.Check, contentDescription = "Not spam")
                }
                IconButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = "Restore to Messages")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete permanently")
                }
            }
        }
    }
}
