package com.smsspam.detector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsspam.detector.ui.AppRoot
import com.smsspam.detector.ui.ComposeTarget
import com.smsspam.detector.ui.AppViewModel
import com.smsspam.detector.ui.theme.SpamGuardTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The app's only activity. It hosts the entire Compose UI through [AppRoot] and
 * also serves as the system entry point for `sms:`/`smsto:` intents, so no
 * separate compose activity is needed.
 */
class MainActivity : ComponentActivity() {

    /** A pending "new message" request from another app, consumed by [AppRoot]. */
    private val composeTarget = MutableStateFlow<ComposeTarget?>(null)

    /** Kept at activity level so lifecycle hooks can reach it. */
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        composeTarget.value = intent?.toComposeTarget()
        setContent {
            val target by composeTarget.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val palette by viewModel.palette.collectAsStateWithLifecycle()
            val pureDark by viewModel.amoledDark.collectAsStateWithLifecycle()
            SpamGuardTheme(themeMode = themeMode, palette = palette, pureDark = pureDark) {
                AppRoot(
                    viewModel = viewModel,
                    composeTarget = target,
                    onComposeTargetConsumed = { composeTarget.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toComposeTarget()?.let { composeTarget.value = it }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is the reset point for the Blocked-tab badge; a
        // rotation stops the activity too but is not an exit.
        if (!isChangingConfigurations) {
            viewModel.markBlockedSeen()
            // Re-lock the app when it goes to the background (no-op when the
            // app lock is off).
            viewModel.lockApp()
        }
    }
}

private val smsSchemes = setOf("sms", "smsto", "mms", "mmsto")

/**
 * Maps an incoming intent to a prefilled compose target, or null when the
 * intent is not an SMS compose request (e.g. the launcher intent).
 */
private fun Intent.toComposeTarget(): ComposeTarget? {
    val scheme = data?.scheme?.lowercase()
    if (scheme != null && scheme !in smsSchemes) return null
    val recipient = data?.schemeSpecificPart?.substringBefore('?')?.trim().orEmpty()
    val body = getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    if (recipient.isBlank() && body.isBlank()) return null
    return ComposeTarget(recipient = recipient, body = body)
}
