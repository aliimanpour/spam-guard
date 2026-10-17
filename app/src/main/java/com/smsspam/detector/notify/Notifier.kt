package com.smsspam.detector.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsspam.detector.MainActivity
import com.smsspam.detector.R
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.text.MessageContent

object Notifier {

    /** Notification for a normal message received while we are the default SMS app. */
    fun notifyMessage(context: Context, sender: String, body: String) {
        val container = (context.applicationContext as? SpamGuardApp)?.container
        // Respect the per-conversation switches and the "unknown numbers" setting.
        val conversation = container?.conversationSettings?.get(sender)
        if (conversation?.notify == false) return
        if (container?.settingsStore?.hideUnknownNotifications == true &&
            !com.smsspam.detector.contacts.ContactsResolver.isKnown(context, sender)
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val preview = body.replace('\n', ' ').take(110)
        val builder = NotificationCompat.Builder(context, SpamGuardApp.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender.ifBlank { "Unknown" })
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            // Per-conversation sound switch: silently suppress sound/vibration.
            .setSilent(conversation?.sound == false)

        // Surface the parts of the message worth acting on right from the shade:
        // the OTP and account numbers become copy buttons, links become an
        // "open" button. Request codes are derived from the sender's hash so the
        // same sender updates its own actions instead of leaking new intents.
        val base = sender.hashCode()
        MessageContent.otpCode(body)?.let { code ->
            builder.addAction(
                0,
                "Copy code",
                NotificationActionReceiver.copyPendingIntent(context, code, "code", base + 1),
            )
        }
        MessageContent.firstUrl(body)?.let { url ->
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val open = PendingIntent.getActivity(
                context,
                base + 2,
                view,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Open link", open)
        }
        MessageContent.firstAccount(body)?.let { account ->
            val label = if (account.kind == MessageContent.Kind.SHEBA) "SHEBA number" else "card number"
            builder.addAction(
                0,
                "Copy number",
                NotificationActionReceiver.copyPendingIntent(context, account.value, label, base + 3),
            )
        }

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    /**
     * Confirms a conversation PDF export and opens it when the notification is
     * tapped.
     */
    fun notifyExport(context: Context, uri: Uri, title: String, count: Int) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pending = PendingIntent.getActivity(
            context,
            uri.hashCode(),
            view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SpamGuardApp.EXPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Conversation exported")
            .setContentText("$title · $count messages · tap to open the PDF")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title · $count messages · tap to open"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(("export-$title").hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    /** Confirms that a scheduled message was delivered (or failed). */
    fun notifyScheduledResult(context: Context, recipient: String, body: String, success: Boolean) {
        val preview = body.replace('\n', ' ').take(110)
        val notification = NotificationCompat.Builder(context, SpamGuardApp.SCHEDULED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (success) "Scheduled message sent" else "Scheduled message failed")
            .setContentText("To ${recipient.ifBlank { "Unknown" }}: $preview")
            .setStyle(NotificationCompat.BigTextStyle().bigText("To ${recipient.ifBlank { "Unknown" }}: $preview"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(("scheduled-$recipient").hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }
}
