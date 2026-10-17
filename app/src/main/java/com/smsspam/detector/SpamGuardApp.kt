package com.smsspam.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.smsspam.detector.data.AppLockStore
import com.smsspam.detector.data.AutoReplyRepository
import com.smsspam.detector.data.AutomationLog
import com.smsspam.detector.data.ContactsRepository
import com.smsspam.detector.data.ConversationSettingsRepository
import com.smsspam.detector.data.FollowUpRepository
import com.smsspam.detector.data.MessageRepository
import com.smsspam.detector.data.RuleRepository
import com.smsspam.detector.data.SavedMessagesRepository
import com.smsspam.detector.data.ScheduleRepository
import com.smsspam.detector.data.SettingsStore
import com.smsspam.detector.data.SimStore
import com.smsspam.detector.data.UnreadStore
import com.smsspam.detector.data.UnsubscribeLog
import com.smsspam.detector.data.WorkflowRepository
import com.smsspam.detector.sms.SmsAlarmScheduler
import com.smsspam.detector.sms.WorkflowScheduler

/** Manual dependency container so the app stays small and framework-driven. */
class AppContainer(context: Context) {
    val ruleRepository = RuleRepository(context)
    val messageRepository = MessageRepository(context)
    val savedMessagesRepository = SavedMessagesRepository(context)
    val scheduleRepository = ScheduleRepository(context)
    val settingsStore = SettingsStore(context)
    val unreadStore = UnreadStore(context)
    val unsubscribeLog = UnsubscribeLog(context)
    val simStore = SimStore(context)
    val autoReplyRepository = AutoReplyRepository(context)
    val workflowRepository = WorkflowRepository(context)
    val followUpRepository = FollowUpRepository(context)
    val contactsRepository = ContactsRepository(context)
    val automationLog = AutomationLog(context)
    val conversationSettings = ConversationSettingsRepository(context)
    val appLockStore = AppLockStore(context)
}

class SpamGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        // Messages caught by a preset that is no longer shipped (e.g. the old
        // link-only filter) are re-checked and un-blocked when they no longer
        // match any live rule, so false positives leave the Blocked tab.
        container.messageRepository.reconcileRetiredMatches(
            RuleRepository.RETIRED_PRESET_IDS,
            container.ruleRepository.matchingRules(),
        )
        // Alarms are cleared on reboot; re-arm any still-pending scheduled sends.
        container.scheduleRepository.pending().forEach { SmsAlarmScheduler.schedule(this, it) }
        // Same for reminder/follow-up workflows.
        WorkflowScheduler.rescheduleAll(this, container)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                getString(R.string.message_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.message_channel_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SCHEDULED_CHANNEL_ID,
                getString(R.string.scheduled_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.scheduled_channel_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                EXPORT_CHANNEL_ID,
                "Exports",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Conversation PDF exports"
            }
        )
    }

    companion object {
        const val MESSAGE_CHANNEL_ID = "new_message"
        const val SCHEDULED_CHANNEL_ID = "scheduled_sent"
        const val EXPORT_CHANNEL_ID = "exports"
    }
}
