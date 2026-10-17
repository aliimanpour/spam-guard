@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.engine.RuleEngine.matches
import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleAction
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField
import com.smsspam.detector.ui.AppViewModel

@Composable
fun RuleEditorScreen(
    viewModel: AppViewModel,
    ruleId: String?,
    onDone: () -> Unit,
) {
    val existing = remember(ruleId) { ruleId?.let { viewModel.rule(it) } }

    var name by remember(ruleId) { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by remember(ruleId) { mutableStateOf(existing?.enabled ?: true) }
    var action by remember(ruleId) { mutableStateOf(existing?.action ?: RuleAction.BLOCK) }
    var logic by remember(ruleId) { mutableStateOf(existing?.logic ?: RuleLogic.ANY) }
    val conditions = remember(ruleId) {
        (existing?.conditions ?: listOf(Condition())).toMutableStateList()
    }
    var testerText by remember(ruleId) { mutableStateOf("") }

    val canSave = name.isNotBlank() &&
        conditions.isNotEmpty() &&
        conditions.all { it.pattern.isNotBlank() && !RuleEngine.hasInvalidRegex(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New rule" else "Edit rule") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        onClick = {
                            val rule = Rule(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                enabled = enabled,
                                action = action,
                                logic = logic,
                                conditions = conditions.toList(),
                                builtin = existing?.builtin ?: false,
                            )
                            viewModel.upsertRule(rule)
                            onDone()
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save rule")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule name") },
                placeholder = { Text("e.g. Lottery spam") },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Enabled", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Only enabled rules are used to filter SMS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When it matches", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RuleAction.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = action == option,
                            onClick = { action = option },
                            shape = SegmentedButtonDefaults.itemShape(index, RuleAction.entries.size),
                        ) {
                            Text(option.label)
                        }
                    }
                }
                Text(
                    if (action == RuleAction.ALLOW) {
                        "Allow: matching messages are never blocked — for verification codes and other must-deliver SMS."
                    } else {
                        "Block: matching messages are treated as spam and moved to quarantine."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Combine conditions with", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RuleLogic.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = logic == option,
                            onClick = { logic = option },
                            shape = SegmentedButtonDefaults.itemShape(index, RuleLogic.entries.size),
                        ) {
                            Text("${option.symbol} · ${if (option == RuleLogic.ANY) "any" else "all"}")
                        }
                    }
                }
                Text(
                    if (logic == RuleLogic.ANY) {
                        "Spam when at least one condition matches (OR)."
                    } else {
                        "Spam only when every condition matches (AND)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            conditions.forEachIndexed { index, condition ->
                ConditionCard(
                    index = index,
                    condition = condition,
                    canDelete = conditions.size > 1,
                    onChange = { conditions[index] = it },
                    onDelete = { conditions.removeAt(index) },
                )
            }

            OutlinedButton(
                onClick = { conditions.add(Condition()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Column(Modifier.width(8.dp)) {}
                Text("Add condition")
            }

            RuleTester(
                text = testerText,
                onTextChange = { testerText = it },
                rule = Rule(name = name, action = action, logic = logic, conditions = conditions.toList()),
            )
        }
    }
}

@Composable
private fun ConditionCard(
    index: Int,
    condition: Condition,
    canDelete: Boolean,
    onChange: (Condition) -> Unit,
    onDelete: () -> Unit,
) {
    val invalidRegex = RuleEngine.hasInvalidRegex(condition)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Condition ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove condition")
                }
            }

            DropdownField(
                label = "Check",
                options = SmsField.entries,
                selected = condition.field,
                display = { it.label },
                onSelect = { onChange(condition.copy(field = it)) },
            )

            DropdownField(
                label = "Match type",
                options = MatchType.entries,
                selected = condition.type,
                display = { "${it.label}${if (it == MatchType.REGEX || it == MatchType.NOT_REGEX) "  (.*)" else ""}" },
                onSelect = { onChange(condition.copy(type = it)) },
            )

            OutlinedTextField(
                value = condition.pattern,
                onValueChange = { onChange(condition.copy(pattern = it)) },
                label = { Text(if (condition.isRegex) "Regex pattern" else "Text") },
                placeholder = {
                    Text(if (condition.isRegex) "e.g. \\b(win|prize)\\b" else "e.g. لغو11")
                },
                singleLine = true,
                isError = invalidRegex,
                supportingText = if (invalidRegex) {
                    { Text("Invalid regular expression") }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Ignore letter case", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = condition.ignoreCase, onCheckedChange = { onChange(condition.copy(ignoreCase = it)) })
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(display(selected), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleTester(
    text: String,
    onTextChange: (String) -> Unit,
    rule: Rule,
) {
    val matched = remember(text, rule) {
        if (text.isBlank()) null else rule.matches(sender = "SampleSender", body = text)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                matched == null -> MaterialTheme.colorScheme.surfaceContainerHighest
                matched == true && rule.action == RuleAction.ALLOW -> MaterialTheme.colorScheme.primaryContainer
                matched == true -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Try this rule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Sample message") },
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                matched == null -> Text(
                    "Type a sample to test the conditions above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                matched == true && rule.action == RuleAction.ALLOW -> Text(
                    "This message would be allowed through (never blocked).",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                matched == true -> Text(
                    "This message would be flagged as spam.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Text(
                    "This message would not be matched.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
