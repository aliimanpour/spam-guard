@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.sms

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.ui.components.SetDefaultSmsDialog
import com.smsspam.detector.ui.components.defaultSmsRoleIntent
import com.smsspam.detector.ui.components.openDefaultAppsSettings
import com.smsspam.detector.ui.components.pickDateTime
import com.smsspam.detector.ui.theme.SpamGuardTheme

/**
 * Compose screen so Spam Guard can act as the default SMS app: other apps can
 * invoke it with `sms:`/`smsto:` intents to start a new message. Sending is
 * gated behind the default-SMS role, matching the rest of the app.
 */
class ComposeSmsActivity : ComponentActivity() {

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Nothing to do; the UI re-checks the role on the next send. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecipient = intent?.data?.schemeSpecificPart
            ?.substringBefore('?')
            ?.trim()
            .orEmpty()
        val initialBody = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        setContent {
            SpamGuardTheme {
                var showDefaultDialog by remember { mutableStateOf(false) }

                ComposeSmsScreen(
                    initialRecipient = initialRecipient,
                    initialBody = initialBody,
                    onSend = { recipient, body ->
                        if (SmsInbox.isDefaultSmsApp(this)) {
                            val ok = SmsSender.send(this, recipient, body)
                            toast(if (ok) "Message sent" else "Unable to send message")
                            finish()
                        } else {
                            showDefaultDialog = true
                        }
                    },
                    onSchedule = { recipient, body ->
                        if (SmsInbox.isDefaultSmsApp(this)) {
                            pickDateTime(this) { at ->
                                val message = ScheduledMessage(
                                    recipient = recipient.trim(),
                                    body = body,
                                    sendAt = at,
                                )
                                (application as SpamGuardApp).container.scheduleRepository.add(message)
                                SmsAlarmScheduler.schedule(this, message)
                                toast("Message scheduled")
                                finish()
                            }
                        } else {
                            showDefaultDialog = true
                        }
                    },
                    onClose = { finish() },
                )

                if (showDefaultDialog) {
                    SetDefaultSmsDialog(
                        onDismiss = { showDefaultDialog = false },
                        onSetDefault = {
                            showDefaultDialog = false
                            val role = defaultSmsRoleIntent(this)
                            if (role != null) roleLauncher.launch(role) else openDefaultAppsSettings(this)
                        },
                    )
                }
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ComposeSmsScreen(
    initialRecipient: String,
    initialBody: String,
    onSend: (String, String) -> Unit,
    onSchedule: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var recipient by remember { mutableStateOf(initialRecipient) }
    var body by remember { mutableStateOf(initialBody) }
    val canSend = recipient.isNotBlank() && body.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New message", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Spam Guard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSchedule(recipient.trim(), body) }, enabled = canSend) {
                        Icon(Icons.Filled.Schedule, contentDescription = "Schedule send")
                    }
                    IconButton(onClick = { onSend(recipient.trim(), body) }, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("To (phone number)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
