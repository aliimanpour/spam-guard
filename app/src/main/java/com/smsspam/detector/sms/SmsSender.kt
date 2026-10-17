package com.smsspam.detector.sms

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager

/**
 * Sends SMS, transparently splitting long bodies into multipart messages. When
 * Spam Guard holds the default SMS role the outgoing message is also written to
 * the system provider so it shows up in the conversation history.
 */
object SmsSender {

    /** Splits a comma/semicolon separated recipient string into addresses. */
    fun parseRecipients(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun send(context: Context, rawRecipients: String, body: String): Boolean =
        send(context, parseRecipients(rawRecipients), body)

    fun send(context: Context, recipients: List<String>, body: String): Boolean {
        if (recipients.isEmpty() || body.isBlank()) return false
        val manager = manager(context) ?: return false
        return recipients.all { recipient ->
            try {
                val parts = manager.divideMessage(body)
                if (parts.size <= 1) {
                    manager.sendTextMessage(recipient, null, body, null, null)
                } else {
                    manager.sendMultipartTextMessage(recipient, null, parts, null, null)
                }
                persistSent(context, recipient, body)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Writes an outgoing message to the system SMS provider when we may. */
    private fun persistSent(context: Context, recipient: String, body: String) {
        if (!SmsInbox.isDefaultSmsApp(context)) return
        try {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.DATE_SENT, now)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (_: Exception) {
            // Not permitted; the message was still handed to the radio.
        }
    }

    @Suppress("DEPRECATION")
    private fun manager(context: Context): SmsManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    } catch (_: Exception) {
        null
    }
}
