package com.smsspam.detector.sms

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.widget.Toast

/**
 * Delivers "respond via message" quick replies (e.g. rejecting an incoming call
 * with a text). The platform requires the default SMS app to expose this service
 * with the [android.Manifest.permission.SEND_RESPOND_VIA_MESSAGE] permission
 * before the SMS role can be granted.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val recipients = recipients(intent)
        val manager = getSystemService(SmsManager::class.java)

        var sent = 0
        if (manager != null && body.isNotBlank()) {
            recipients.forEach { recipient ->
                try {
                    manager.sendTextMessage(recipient, null, body, null, null)
                    sent++
                } catch (_: Exception) {
                    // Ignore individual failures.
                }
            }
        }

        Toast.makeText(
            this,
            if (sent > 0) "Message sent" else "Unable to send message",
            Toast.LENGTH_SHORT,
        ).show()

        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun recipients(intent: Intent?): List<String> {
        val fromData = intent?.data?.schemeSpecificPart
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        if (!fromData.isNullOrEmpty()) return fromData
        return listOfNotNull(intent?.getStringExtra("android.intent.extra.RECIPIENTS"))
    }
}
