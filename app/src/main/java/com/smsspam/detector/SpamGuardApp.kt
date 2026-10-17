package com.smsspam.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.smsspam.detector.data.MessageRepository
import com.smsspam.detector.data.RuleRepository
import com.smsspam.detector.data.ScheduleRepository
import com.smsspam.detector.sms.SmsAlarmScheduler

/** Manual dependency container so the app stays small and framework-driven. */
class AppContainer(context: Context) {
    val ruleRepository = RuleRepository(context)
    val messageRepository = MessageRepository(context)
    val scheduleRepository = ScheduleRepository(context)
}

class SpamGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        // Alarms are cleared on reboot; re-arm any still-pending scheduled sends.
        container.scheduleRepository.pending().forEach { SmsAlarmScheduler.schedule(this, it) }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                SPAM_CHANNEL_ID,
                getString(R.string.spam_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.spam_channel_description)
            }
        )
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
    }

    companion object {
        const val SPAM_CHANNEL_ID = "spam_caught"
        const val MESSAGE_CHANNEL_ID = "new_message"
        const val SCHEDULED_CHANNEL_ID = "scheduled_sent"
    }
}
