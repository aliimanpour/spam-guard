package com.smsspam.detector.ui.components

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Lock gate used for the whole app and for individually locked conversations.
 * Offers the app PIN and, when available, the device credential (fingerprint,
 * face or the system PIN/pattern).
 */
@Composable
fun LockScreen(
    title: String,
    subtitle: String,
    hasPin: Boolean,
    allowDeviceCredential: Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val deviceSecure = remember { keyguard?.isDeviceSecure == true }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        if (allowDeviceCredential && deviceSecure) {
            OutlinedButton(
                onClick = {
                    val intent = keyguard?.createConfirmDeviceCredentialIntent(null, null)
                    if (intent != null) credentialLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Text("  Unlock with fingerprint / face / PIN")
            }
        }

        // No credential configured: don't lock the user out of their own app.
        if (!hasPin && !(allowDeviceCredential && deviceSecure)) {
            Text(
                text = "No app PIN or device lock is set up. Set one in Settings → Privacy & security.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(onClick = onUnlocked, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }

        if (hasPin) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it; error = false },
                label = { Text("App PIN") },
                singleLine = true,
                isError = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                supportingText = if (error) {
                    { Text("Wrong PIN") }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (allowDeviceCredential && deviceSecure) 12.dp else 0.dp),
            )
            Button(
                onClick = {
                    if (onUnlockWithPin(pin)) onUnlocked() else error = true
                },
                enabled = pin.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Unlock")
            }
        }
    }
}
