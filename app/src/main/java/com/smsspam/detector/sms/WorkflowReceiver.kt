package com.smsspam.detector.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsspam.detector.SpamGuardApp

/**
 * Fires reminders and follow-ups scheduled by [WorkflowScheduler], and re-arms
 * them after a reboot or an app update.
 */
class WorkflowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SpamGuardApp ?: return
        when (intent.action) {
            WorkflowScheduler.ACTION_WORKFLOW -> {
                val id = intent.getStringExtra(WorkflowScheduler.EXTRA_ID) ?: return
                when (intent.getStringExtra(WorkflowScheduler.EXTRA_KIND)) {
                    WorkflowScheduler.KIND_REMINDER -> runReminder(app, id)
                    WorkflowScheduler.KIND_FOLLOW_UP -> runFollowUp(app, id)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkflowScheduler.rescheduleAll(app, app.container)
                Log.i(TAG, "rescheduled reminders and follow-ups after boot/update")
            }
        }
    }

    private fun runReminder(app: SpamGuardApp, id: String) {
        val workflow = app.container.workflowRepository.byId(id) ?: return
        if (!workflow.enabled) return
        val recipients = AutomationRunner.recipientsFor(app.container, workflow)
        if (recipients.isEmpty()) return
        val sent = SmsSender.send(app, recipients, workflow.message, triggerFollowUp = false)
        // One-shot: disable so it never fires again on the next reschedule.
        app.container.workflowRepository.setEnabled(id, false)
        Log.i(TAG, "reminder '${workflow.name}' to=${recipients.size} sent=$sent")
    }

    private fun runFollowUp(app: SpamGuardApp, id: String) {
        val item = app.container.followUpRepository.all().firstOrNull { it.id == id } ?: return
        val sent = SmsSender.send(app, item.recipient, item.message, triggerFollowUp = false)
        app.container.followUpRepository.delete(id)
        Log.i(TAG, "follow-up to=${item.recipient} sent=$sent")
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
