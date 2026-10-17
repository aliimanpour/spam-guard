@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsspam.detector.BuildConfig
import com.smsspam.detector.data.SettingsStore
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.theme.ThemeMode
import com.smsspam.detector.ui.theme.ThemePalette
import com.smsspam.detector.ui.components.AppPermission
import com.smsspam.detector.ui.components.InfoCard
import com.smsspam.detector.ui.components.batteryOptimizationSettingsIntent
import com.smsspam.detector.ui.components.hasPermission
import com.smsspam.detector.ui.components.isIgnoringBatteryOptimizations
import com.smsspam.detector.ui.components.oemAutoStartIntent
import com.smsspam.detector.ui.components.oemAutoStartLabel
import com.smsspam.detector.ui.components.oemName
import com.smsspam.detector.ui.components.rememberPermissionStatus
import com.smsspam.detector.ui.components.requestIgnoreBatteryOptimizationsIntent

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val permissionStatus = rememberPermissionStatus()
    val autoUnsubscribe by viewModel.autoUnsubscribe.collectAsStateWithLifecycle()
    val unsubscribeCode by viewModel.unsubscribeCode.collectAsStateWithLifecycle()
    val deliveryReports by viewModel.deliveryReports.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val amoledDark by viewModel.amoledDark.collectAsStateWithLifecycle()
    val hideUnknown by viewModel.hideUnknownNotifications.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockDevice by viewModel.appLockDeviceCredential.collectAsStateWithLifecycle()
    val smartReplies by viewModel.smartReplies.collectAsStateWithLifecycle()
    val autoRetry by viewModel.autoRetry.collectAsStateWithLifecycle()
    var showPinDialog by remember { mutableStateOf(false) }
    var ignoringBattery by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }

    // The battery-optimisation choice is made in a system screen, so re-read it
    // every time Settings comes back to the foreground.
    LifecycleResumeEffect(Unit) {
        ignoringBattery = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

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
            SectionTitle("Appearance")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Choose light, dark, or follow the system setting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pure black (AMOLED)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Paint dark themes with true black to save power on AMOLED screens.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = amoledDark,
                            onCheckedChange = { viewModel.setAmoledDark(it) },
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Color palette",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pick an accent color, or follow your wallpaper with the system palette.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemePalette.entries.forEach { option ->
                            FilterChip(
                                selected = palette == option,
                                onClick = { viewModel.setPalette(option) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(option.swatch),
                                    )
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }

            SectionTitle("Notifications")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Hide unknown numbers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Don't show notifications for messages from numbers that are not in " +
                                "your contacts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = hideUnknown,
                        onCheckedChange = { viewModel.setHideUnknownNotifications(it) },
                    )
                }
            }

            SectionTitle("Notifications on time")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (ignoringBattery) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (ignoringBattery) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = if (ignoringBattery) {
                                "Battery optimisation is off for Spam Guard"
                            } else {
                                "Battery optimisation may delay notifications"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Some phones (" + (oemName() ?: "Xiaomi, Samsung, Huawei") + ") put apps to " +
                            "sleep and hold back new-message alerts. Allow Spam Guard to run in the " +
                            "background so notifications arrive on time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = if (ignoringBattery) {
                                batteryOptimizationSettingsIntent()
                            } else {
                                requestIgnoreBatteryOptimizationsIntent(context)
                            }
                            safeStartActivity(context, intent)
                        }) {
                            Text(if (ignoringBattery) "Battery settings" else "Allow background")
                        }
                        oemAutoStartIntent(context)?.let { autoStart ->
                            OutlinedButton(onClick = { safeStartActivity(context, autoStart) }) {
                                Text(oemAutoStartLabel() ?: "Auto-start")
                            }
                        }
                    }
                    if (!ignoringBattery && oemAutoStartIntent(context) != null) {
                        Text(
                            "Also open " + (oemAutoStartLabel() ?: "auto-start") +
                                " and allow Spam Guard to start automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Privacy & security")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "App lock",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Require an unlock every time Spam Guard opens.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) },
                        )
                    }
                    if (appLockEnabled) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Device unlock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Allow fingerprint, face or the system PIN to unlock.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = appLockDevice,
                                onCheckedChange = { viewModel.setAppLockDeviceCredential(it) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showPinDialog = true }) {
                                Text(if (viewModel.appLockHasPin()) "Change PIN" else "Set app PIN")
                            }
                        }
                        Text(
                            "Individual conversations can also be locked from their menu; " +
                                "they unlock with the same PIN or device credential.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

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

            SectionTitle("Sending")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Retry without signal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "When a message fails because there is no signal (or airplane mode " +
                                "is on), keep it in the conversation and retry automatically with a " +
                                "growing delay.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoRetry,
                        onCheckedChange = { viewModel.setAutoRetry(it) },
                    )
                }
            }

            SectionTitle("Delivery reports")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Read receipts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Ask the network for a delivery report so delivered messages show a " +
                                "second tick (✓✓). Turn off to send without reports.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = deliveryReports,
                        onCheckedChange = { viewModel.setDeliveryReports(it) },
                    )
                }
            }

            SectionTitle("Smart replies")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Suggest replies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Offer ready-made Persian/English replies above the message box, " +
                                "picked on your device from the words in the last message. " +
                                "Nothing is sent to a server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = smartReplies,
                        onCheckedChange = { viewModel.setSmartReplies(it) },
                    )
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
                    "Restore Spam Protection, the built-in filters that ship with the app. " +
                        "Your custom rules are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.resetProtection() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Reset Spam Protection")
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

    if (showPinDialog) {
        PinSetupDialog(
            hasPin = viewModel.appLockHasPin(),
            onDismiss = { showPinDialog = false },
            onSave = { pin -> viewModel.setAppPin(pin); showPinDialog = false },
            onRemove = { viewModel.clearAppPin(); showPinDialog = false },
        )
    }
}

@Composable
private fun PinSetupDialog(
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length >= 4 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Change app PIN" else "Set app PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("New PIN (4–8 digits)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != pin,
                )
                if (hasPin) {
                    TextButton(onClick = onRemove) { Text("Remove PIN") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pin) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** Starts a system settings screen, swallowing the case where it does not exist. */
private fun safeStartActivity(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        openDefaultAppsSettings(context)
    }
}
