package com.smsspam.detector.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smsspam.detector.model.ScheduledMessage

/**
 * Wires [ScheduledMessage]s to [AlarmManager]. Uses exact alarms when the app is
 * allowed to, otherwise falls back to inexact `setAndAllowWhileIdle` so the
 * message still goes out without requesting the special alarm permission.
 */
object SmsAlarmScheduler {

    const val ACTION_SEND_SCHEDULED = "com.smsspam.detector.action.SEND_SCHEDULED"
    const val EXTRA_ID = "scheduled_id"

    fun schedule(context: Context, message: ScheduledMessage) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, message.id)
        val triggerAt = message.sendAt.coerceAtLeast(System.currentTimeMillis() + 1_000)
        try {
            if (canScheduleExact(alarm)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pendingIntent(context, id))
    }

    private fun canScheduleExact(alarm: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, SmsAlarmReceiver::class.java).apply {
            action = ACTION_SEND_SCHEDULED
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
