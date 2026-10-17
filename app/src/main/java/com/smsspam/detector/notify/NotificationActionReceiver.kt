package com.smsspam.detector.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Handles the "copy" actions attached to message notifications: copying an
 * one-time-password code or a card / SHEBA number to the clipboard. Opening a
 * link is left to a normal `ACTION_VIEW` intent, so it never reaches here.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY) return
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label.ifEmpty { "SMS" }, text))
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY = "com.smsspam.detector.action.NOTIFICATION_COPY"
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_LABEL = "extra_label"

        /** A broadcast PendingIntent that copies [text] to the clipboard. */
        fun copyPendingIntent(
            context: Context,
            text: String,
            label: String,
            requestCode: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COPY
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_LABEL, label)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
