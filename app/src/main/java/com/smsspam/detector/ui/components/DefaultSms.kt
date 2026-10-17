package com.smsspam.detector.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Intent that asks the system to make Spam Guard the default SMS app, or null
 * when the role is unavailable (API &lt; 29 or a device without telephony).
 */
fun defaultSmsRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val manager = context.getSystemService(RoleManager::class.java) ?: return null
    return if (manager.isRoleAvailable(RoleManager.ROLE_SMS)) {
        manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        null
    }
}

/** Opens the system "Default apps" screen as a fallback when the role prompt fails. */
fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Blocking prompt shown before sending: Android only lets the default SMS app
 * write to the message store, so we ask the user to take the role first.
 */
@Composable
fun SetDefaultSmsDialog(
    onDismiss: () -> Unit,
    onSetDefault: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Sms, contentDescription = null) },
        title = { Text("Set as your default SMS app") },
        text = {
            Text(
                "To send and save text messages, Spam Guard needs to be your default SMS app. " +
                    "Android only lets the default app write to the message store.",
            )
        },
        confirmButton = {
            TextButton(onClick = onSetDefault) { Text("Set as default") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
