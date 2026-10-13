package com.smsspam.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.smsspam.detector.data.MessageRepository
import com.smsspam.detector.data.RuleRepository

/** Manual dependency container so the app stays small and framework-driven. */
class AppContainer(context: Context) {
    val ruleRepository = RuleRepository(context)
    val messageRepository = MessageRepository(context)
}

class SpamGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
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
    }

    companion object {
        const val SPAM_CHANNEL_ID = "spam_caught"
        const val MESSAGE_CHANNEL_ID = "new_message"
    }
}
