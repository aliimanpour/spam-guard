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

        val outcome = SmsSender.sendOutcome(
            app,
            message.recipient,
            message.body,
            message.subId,
            existingRowId = message.providerRowId.takeIf { it > 0L } ?: 0L,
        )
        if (outcome.ok) {
            repository.updateStatus(id, ScheduledStatus.SENT)
            Notifier.notifyScheduledResult(app, message.recipient, message.body, true)
            Log.i(TAG, "send id=$id success=true")
            return
        }

        val attempts = message.attempts + 1
        // A transient failure (no signal / airplane mode) is queued for another
        // automatic attempt with a growing backoff. Anything else is final.
        val requeued = if (outcome.failure.retriable) {
            SendQueue.enqueue(
                app,
                message.recipient,
                message.body,
                message.subId,
                outcome.failure,
                attempts,
                message.providerRowId,
            )
        } else {
            null
        }
        if (requeued != null && requeued.id == id) {
            Log.i(TAG, "send id=$id failed (${outcome.failure.message}); retry #$attempts at ${requeued.sendAt}")
        } else {
            repository.update(
                message.copy(
                    status = ScheduledStatus.FAILED,
                    attempts = attempts,
                    lastError = outcome.failure.message,
                )
            )
            Notifier.notifyScheduledResult(app, message.recipient, message.body, false)
            Log.i(TAG, "send id=$id failed permanently: ${outcome.failure.message}")
        }
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
