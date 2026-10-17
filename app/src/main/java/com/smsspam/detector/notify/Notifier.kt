package com.smsspam.detector.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsspam.detector.MainActivity
import com.smsspam.detector.R
import com.smsspam.detector.SpamGuardApp

object Notifier {

    /** Notification for a normal message received while we are the default SMS app. */
    fun notifyMessage(context: Context, sender: String, body: String) {
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
        val notification = NotificationCompat.Builder(context, SpamGuardApp.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender.ifBlank { "Unknown" })
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
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
