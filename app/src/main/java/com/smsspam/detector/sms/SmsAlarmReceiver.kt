package com.smsspam.detector.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.model.ScheduledStatus
import com.smsspam.detector.notify.Notifier

/**
 * Fires when a scheduled message is due and reschedules pending messages after a
 * reboot or an app update.
 */
class SmsAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SpamGuardApp ?: return
        when (intent.action) {
            SmsAlarmScheduler.ACTION_SEND_SCHEDULED -> {
                val id = intent.getStringExtra(SmsAlarmScheduler.EXTRA_ID) ?: return
                sendScheduled(app, id)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                reschedulePending(app)
            }
        }
    }

    private fun sendScheduled(app: SpamGuardApp, id: String) {
        val repository = app.container.scheduleRepository
        val message = repository.scheduled.value.firstOrNull { it.id == id } ?: return
        if (message.status != ScheduledStatus.PENDING) return

        val sent = SmsSender.send(app, message.recipient, message.body)
        repository.updateStatus(id, if (sent) ScheduledStatus.SENT else ScheduledStatus.FAILED)
        Notifier.notifyScheduledResult(app, message.recipient, message.body, sent)
        Log.i(TAG, "scheduled send id=$id success=$sent")
    }

    private fun reschedulePending(app: SpamGuardApp) {
        app.container.scheduleRepository.pending().forEach {
            SmsAlarmScheduler.schedule(app, it)
        }
        Log.i(TAG, "rescheduled pending scheduled messages after boot/update")
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
