@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsspam.detector.BuildConfig
import com.smsspam.detector.data.SettingsStore
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.components.AppPermission
import com.smsspam.detector.ui.components.InfoCard
import com.smsspam.detector.ui.components.hasPermission
import com.smsspam.detector.ui.components.rememberPermissionStatus

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val permissionStatus = rememberPermissionStatus()
    val autoUnsubscribe by viewModel.autoUnsubscribe.collectAsStateWithLifecycle()
    val unsubscribeCode by viewModel.unsubscribeCode.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus.value = AppPermission.needed.associateWith { context.hasPermission(it.permission) }
        viewModel.refreshSims(force = true)
    }

    var isDefaultSms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                false
            }
        )
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isDefaultSms = context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_SMS) == true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportData(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importData(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
            SectionTitle("Permissions")
            InfoCard {
                AppPermission.needed.forEach { permission ->
                    PermissionRow(
                        title = permission.title,
                        rationale = permission.rationale,
                        granted = permissionStatus.value[permission] == true,
                    )
                }
                val missing = AppPermission.needed.filter { permissionStatus.value[it] != true }
                if (missing.isNotEmpty()) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(missing.map { it.permission }.toTypedArray())
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Grant missing permissions")
                    }
                }
            }

            SectionTitle("Default SMS app")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isDefaultSms) "Spam Guard is your default SMS app"
                        else "Not the default SMS app",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Android only lets the default SMS app delete messages from the system inbox. " +
                            "Without that role, blocked messages are still copied here but stay in your Messages app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isDefaultSms) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        val canRequestRole = roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true
                        if (canRequestRole) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                                    } catch (_: Exception) {
                                        openDefaultAppsSettings(context)
                                    }
                                },
                            ) {
                                Text("Set as default")
                            }
                        }
                        TextButton(onClick = { openDefaultAppsSettings(context) }) {
                            Text("Open system default apps")
                        }
                    }
                }
            }

            SectionTitle("Auto unsubscribe")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Unsubscribe for me",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "When a blocked ad asks for an unsubscribe code (لغو11), send that " +
                                    "code back to its sender — once per sender.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoUnsubscribe,
                            onCheckedChange = { viewModel.setAutoUnsubscribe(it) },
                        )
                    }
                    if (autoUnsubscribe) {
                        Text("Code to send", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SettingsStore.CODE_CHOICES.forEachIndexed { index, code ->
                                SegmentedButton(
                                    selected = unsubscribeCode == code,
                                    onClick = { viewModel.setUnsubscribeCode(code) },
                                    shape = SegmentedButtonDefaults.itemShape(index, SettingsStore.CODE_CHOICES.size),
                                ) {
                                    Text(if (code == SettingsStore.CODE_AUTO) "Auto" else code)
                                }
                            }
                        }
                        Text(
                            if (unsubscribeCode == SettingsStore.CODE_AUTO) {
                                "Auto: replies with the code the ad itself mentions (لغو12 → 12), otherwise 11."
                            } else {
                                "Always replies with $unsubscribeCode, whatever the ad mentions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Rules")
            InfoCard {
                Text(
                    "Restore the rules that ship with the app. Your custom rules are replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.resetRules() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Restore preset rules")
                }
            }

            SectionTitle("Backup")
            InfoCard {
                Text(
                    "Export your rules, blocked messages and scheduled messages to a single JSON file, " +
                        "then import it on another device. Importing replaces your rules and merges messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { exportLauncher.launch("spam-guard-backup.json") }) {
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    ) {
                        Text("Import")
                    }
                }
            }

            SectionTitle("About")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Spam Guard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text(
                        "Filter spam SMS with your own rules. Each rule combines conditions with AND/OR " +
                            "and can match text, the sender or a regular expression. Blocked messages are kept " +
                            "here so you can restore anything that was flagged by mistake.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PermissionRow(title: String, rationale: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
