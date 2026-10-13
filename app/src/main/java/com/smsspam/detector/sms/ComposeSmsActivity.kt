@file:OptIn(ExperimentalMaterial3Api::class)

package com.smsspam.detector.sms

import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsspam.detector.ui.theme.SpamGuardTheme

/**
 * Minimal compose screen so Spam Guard can act as the default SMS app: other apps
 * can invoke it with `sms:`/`smsto:` intents to start a new message.
 */
class ComposeSmsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecipient = intent?.data?.schemeSpecificPart
            ?.substringBefore('?')
            ?.trim()
            .orEmpty()
        val initialBody = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty()

        setContent {
            SpamGuardTheme {
                ComposeSmsScreen(
                    initialRecipient = initialRecipient,
                    initialBody = initialBody,
                    onSend = { recipient, body ->
                        val ok = send(recipient, body)
                        Toast.makeText(
                            this,
                            if (ok) "Message sent" else "Unable to send message",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun send(recipient: String, body: String): Boolean {
        val manager = getSystemService(SmsManager::class.java) ?: return false
        return try {
            manager.sendTextMessage(recipient, null, body, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
private fun ComposeSmsScreen(
    initialRecipient: String,
    initialBody: String,
    onSend: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var recipient by remember { mutableStateOf(initialRecipient) }
    var body by remember { mutableStateOf(initialBody) }
    val canSend = recipient.isNotBlank() && body.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("To") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSend(recipient.trim(), body) },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("Send", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
