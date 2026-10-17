package com.smsspam.detector.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smsspam.detector.AppContainer
import com.smsspam.detector.model.PendingFollowUp
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTrigger

/**
 * Wires reminders and follow-ups to [AlarmManager]. Mirrors
 * [SmsAlarmScheduler]: exact alarms when allowed, otherwise an inexact
 * `setAndAllowWhileIdle` fallback so no special permission is required.
 */
object WorkflowScheduler {

    const val ACTION_WORKFLOW = "com.smsspam.detector.action.RUN_WORKFLOW"
    const val EXTRA_KIND = "workflow_kind"
    const val EXTRA_ID = "workflow_id"

    const val KIND_REMINDER = "reminder"
    const val KIND_FOLLOW_UP = "follow_up"

    fun scheduleReminder(context: Context, workflow: Workflow) {
        if (workflow.trigger != WorkflowTrigger.REMINDER) return
        schedule(context, "rem:${workflow.id}", workflow.triggerAt, KIND_REMINDER, workflow.id)
    }

    fun cancelReminder(context: Context, workflowId: String) {
        cancel(context, "rem:$workflowId", KIND_REMINDER, workflowId)
    }

    fun scheduleFollowUp(context: Context, item: PendingFollowUp) {
        schedule(context, "fu:${item.id}", item.dueAt, KIND_FOLLOW_UP, item.id)
    }

    fun cancelFollowUp(context: Context, id: String) {
        cancel(context, "fu:$id", KIND_FOLLOW_UP, id)
    }

    /** Re-arms every future reminder and pending follow-up (boot / app update). */
    fun rescheduleAll(context: Context, container: AppContainer) {
        val now = System.currentTimeMillis()
        container.workflowRepository.enabled()
            .filter { it.trigger == WorkflowTrigger.REMINDER && it.triggerAt > now }
            .forEach { scheduleReminder(context, it) }
        container.followUpRepository.all()
            .filter { it.dueAt > now }
            .forEach { scheduleFollowUp(context, it) }
    }

    private fun schedule(context: Context, slot: String, at: Long, kind: String, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = at.coerceAtLeast(System.currentTimeMillis() + 1_000)
        val pending = pendingIntent(context, slot, kind, id)
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

    private fun cancel(context: Context, slot: String, kind: String, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pendingIntent(context, slot, kind, id))
    }

    private fun canScheduleExact(alarm: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, slot: String, kind: String, id: String): PendingIntent {
        val intent = Intent(context, WorkflowReceiver::class.java).apply {
            action = ACTION_WORKFLOW
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            slot.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
