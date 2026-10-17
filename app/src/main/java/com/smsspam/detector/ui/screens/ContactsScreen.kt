@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.smsspam.detector.model.ContactEntry
import com.smsspam.detector.model.ContactGroup
import com.smsspam.detector.ui.AppViewModel

/** Manage the local address book: contacts with tags, and named groups. */
@Composable
fun ContactsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val contacts by viewModel.contactBook.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    var contactEditor by remember { mutableStateOf<ContactEntry?>(null) }
    var groupEditor by remember { mutableStateOf<ContactGroup?>(null) }
    var pendingDeleteContact by remember { mutableStateOf<ContactEntry?>(null) }
    var pendingDeleteGroup by remember { mutableStateOf<ContactGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups & tags") },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tags and groups segment your contacts so you can message the right people " +
                        "in one go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionHeader("Contacts", "New contact") { contactEditor = ContactEntry() }
            }
            if (contacts.isEmpty()) {
                item { EmptyHint("No contacts yet.") }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onEdit = { contactEditor = contact },
                        onDelete = { pendingDeleteContact = contact },
                    )
                }
            }

            item {
                SectionHeader("Groups", "New group") { groupEditor = ContactGroup() }
            }
            if (groups.isEmpty()) {
                item { EmptyHint("No groups yet.") }
            } else {
                items(groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onEdit = { groupEditor = group },
                        onDelete = { pendingDeleteGroup = group },
                    )
                }
            }
        }
    }

    contactEditor?.let { editing ->
        ContactEditorDialog(
            initial = editing,
            onDismiss = { contactEditor = null },
            onSave = { viewModel.saveContact(it); contactEditor = null },
        )
    }

    groupEditor?.let { editing ->
        GroupEditorDialog(
            initial = editing,
            onDismiss = { groupEditor = null },
            onSave = { viewModel.saveGroup(it); groupEditor = null },
        )
    }

    pendingDeleteContact?.let { contact ->
        ConfirmDelete(
            title = "Delete contact?",
            text = "“${contact.name.ifBlank { contact.number }}” will be removed.",
            onDismiss = { pendingDeleteContact = null },
            onConfirm = { viewModel.deleteContact(contact.id); pendingDeleteContact = null },
        )
    }

    pendingDeleteGroup?.let { group ->
        ConfirmDelete(
            title = "Delete group?",
            text = "“${group.name.ifBlank { "Untitled" }}” will be removed.",
            onDismiss = { pendingDeleteGroup = null },
            onConfirm = { viewModel.deleteGroup(group.id); pendingDeleteGroup = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  $action")
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ContactCard(
    contact: ContactEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = contact.name.ifBlank { contact.number },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = contact.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (contact.tags.isNotEmpty()) {
                    Text(
                        text = contact.tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Contact options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
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

@Composable
private fun GroupCard(
    group: ContactGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = group.name.ifBlank { "Untitled group" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.members.size} member${if (group.members.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(onClick = { }, label = { Text("Group") })
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
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

@Composable
private fun ContactEditorDialog(
    initial: ContactEntry,
    onDismiss: () -> Unit,
    onSave: (ContactEntry) -> Unit,
) {
    var number by remember { mutableStateOf(initial.number) }
    var name by remember { mutableStateOf(initial.name) }
    var tags by remember { mutableStateOf(initial.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.number.isBlank()) "New contact" else "Edit contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("customer, vip") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            number = number.trim(),
                            name = name.trim(),
                            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        )
                    )
                },
                enabled = number.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupEditorDialog(
    initial: ContactGroup,
    onDismiss: () -> Unit,
    onSave: (ContactGroup) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var members by remember { mutableStateOf(initial.members.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "New group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = members,
                    onValueChange = { members = it },
                    label = { Text("Members (comma separated, max 100)") },
                    minLines = 2,
                    isError = members.split(',', '\n').count { it.isNotBlank() } > 100,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            members = members.split(',', '\n').map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .take(100),
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDelete(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
