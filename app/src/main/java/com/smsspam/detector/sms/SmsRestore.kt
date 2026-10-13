package com.smsspam.detector.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.smsspam.detector.model.QuarantineMessage

/**
 * Puts a message back into the system SMS app. On modern Android the SMS
 * provider can only be written by the default SMS app, so this is best effort
 * and reports success/failure.
 */
fun restoreToInbox(context: Context, message: QuarantineMessage): Boolean = try {
    val values = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, message.sender)
        put(Telephony.Sms.BODY, message.body)
        put(Telephony.Sms.DATE, message.timestamp)
        put(Telephony.Sms.DATE_SENT, message.timestamp)
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
    }
    context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
} catch (_: Exception) {
    false
}
