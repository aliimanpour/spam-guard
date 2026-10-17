@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smsspam.detector.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.contacts.Recipient
import com.smsspam.detector.contacts.RecipientDirectory
import com.smsspam.detector.engine.AutomationEngine
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.ContactAvatar
import com.smsspam.detector.ui.components.pickDateTime

private const val MAX_RECIPIENTS = 100

/**
 * A brand-new message that can go to several recipients at once. Recipients are
 * chosen from a searchable list (with checkboxes), the app's contact book, or a
 * saved group. Up to [MAX_RECIPIENTS] recipients per message.
 */
@Composable
fun ComposeScreen(
    viewModel: AppViewModel,
    initialRecipient: String = "",
    initialBody: String = "",
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    var selected by remember {
        mutableStateOf(if (initialRecipient.isBlank()) emptyList() else listOf(initialRecipient))
    }
    var query by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(initialBody) }
    var showGroupPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshRecipients() }

    val suggestions = remember(recipients, query) {
        if (query.isBlank()) emptyList() else RecipientDirectory.search(recipients, query).take(20)
    }
    val canSend = selected.isNotEmpty() && body.isNotBlank()

    val addRecipients: (List<String>) -> Unit = { numbers ->
        selected = AutomationEngine.dedupeNumbers(selected + numbers).take(MAX_RECIPIENTS)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("New message") },
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
                        ComposeInputField(
                            value = body,
                            onValueChange = { body = it },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                pickDateTime(context) { at ->
                                    viewModel.scheduleBulk(selected, body, at)
                                    onDone()
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Schedule send")
                        }
                        FilledIconButton(
                            onClick = {
                                viewModel.sendBulk(selected, body)
                                onDone()
                            },
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name or number") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        focusManager.clearFocus()
                        showGroupPicker = true
                    }) {
                        Icon(Icons.Filled.Group, contentDescription = null)
                        Text("  Pick group")
                    }
                }

                if (selected.isNotEmpty()) {
                    Text(
                        "${selected.size} recipient${if (selected.size == 1) "" else "s"}" +
                            if (selected.size >= MAX_RECIPIENTS) " (max $MAX_RECIPIENTS)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.forEach { number ->
                            AssistChip(
                                onClick = { selected = selected - number },
                                label = { Text(number, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }
            }

            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        // Cap the dropdown to the space left above the keyboard
                        // so its rows stay reachable while typing.
                        .weight(1f, fill = false),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(suggestions, key = { it.number }) { suggestion ->
                            val checked = selected.any { AutomationEngine.sameNumber(it, suggestion.number) }
                            SuggestionRow(
                                suggestion = suggestion,
                                checked = checked,
                                onToggle = {
                                    if (checked) {
                                        selected = selected.filterNot {
                                            AutomationEngine.sameNumber(it, suggestion.number)
                                        }
                                    } else {
                                        addRecipients(listOf(suggestion.number))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Pick a group") },
            text = {
                if (groups.isEmpty()) {
                    Text("No groups yet. Create one from Messages → Groups & contacts.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        groups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        addRecipients(group.members)
                                        showGroupPicker = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Group, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name.ifBlank { "Untitled group" })
                                    Text(
                                        "${group.members.size} members",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupPicker = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Recipient,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        ContactAvatar(label = suggestion.label, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(suggestion.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (suggestion.name != null && suggestion.number != suggestion.name) {
                Text(
                    suggestion.number,
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
private fun ComposeInputField(
    value: String,
    onValueChange: (String) -> Unit,
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
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
