package com.smsspam.detector.sms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.smsspam.detector.SpamGuardApp
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends SMS, transparently splitting long bodies into multipart messages. When
 * Spam Guard holds the default SMS role the outgoing message is also written to
 * the system provider so it shows up in the conversation history.
 *
 * Every send also requests a delivery report (unless the user turned it off in
 * Settings). The report comes back to [SmsStatusReceiver], which flips the
 * provider row's `status` so the UI can render the second tick.
 */
object SmsSender {

    const val ACTION_SMS_SENT = "com.smsspam.detector.action.SMS_SENT"
    const val ACTION_SMS_DELIVERED = "com.smsspam.detector.action.SMS_DELIVERED"
    const val EXTRA_ROW_ID = "sms_row_id"

    /** Distinct request code per PendingIntent so reports never collide. */
    private val requestCodes = AtomicInteger(1)

    /** Splits a comma/semicolon separated recipient string into addresses. */
    fun parseRecipients(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun send(context: Context, rawRecipients: String, body: String, subId: Int = 0): Boolean =
        send(context, parseRecipients(rawRecipients), body, subId)

    /**
     * Sends with the SIM chosen by [subId]; 0 means the system default SIM.
     * A subscription that is no longer active (SIM swapped out) falls back to
     * the default so the message still goes out.
     */
    fun send(context: Context, recipients: List<String>, body: String, subId: Int = 0): Boolean {
        if (recipients.isEmpty() || body.isBlank()) return false
        val manager = manager(context, subId) ?: return false
        val wantReport = deliveryReportsEnabled(context)
        return recipients.all { recipient ->
            try {
                // Write the row first so the delivery/sent reports have a row id
                // to update. Null when we are not the default app (nothing to
                // update then, and reports are skipped).
                val rowId = persistSent(context, recipient, body)
                val sentIntent = statusIntent(context, recipient, rowId, ACTION_SMS_SENT, wantReport)
                val deliveredIntent = statusIntent(context, recipient, rowId, ACTION_SMS_DELIVERED, wantReport)
                val parts = manager.divideMessage(body)
                if (parts.size <= 1) {
                    manager.sendTextMessage(recipient, null, body, sentIntent, deliveredIntent)
                } else {
                    val sentIntents = sentIntent?.let { intent ->
                        ArrayList<PendingIntent>(parts.size).apply { repeat(parts.size) { add(intent) } }
                    }
                    val deliveredIntents = deliveredIntent?.let { intent ->
                        ArrayList<PendingIntent>(parts.size).apply { repeat(parts.size) { add(intent) } }
                    }
                    manager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveredIntents)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Writes an outgoing message to the system SMS provider when we may. */
    private fun persistSent(context: Context, recipient: String, body: String): Long? {
        if (!SmsInbox.isDefaultSmsApp(context)) return null
        return try {
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
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)?.let(ContentUris::parseId)
        } catch (_: Exception) {
            // Not permitted; the message was still handed to the radio.
            null
        }
    }

    /** Builds a distinct delivery/sent report PendingIntent for [rowId]. */
    private fun statusIntent(
        context: Context,
        recipient: String,
        rowId: Long?,
        action: String,
        enabled: Boolean,
    ): PendingIntent? {
        if (!enabled || rowId == null || rowId <= 0L) return null
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ROW_ID, rowId)
            putExtra(Telephony.Sms.ADDRESS, recipient)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodes.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** True unless the user turned delivery reports off in Settings (default on). */
    private fun deliveryReportsEnabled(context: Context): Boolean =
        (context.applicationContext as? SpamGuardApp)?.container?.settingsStore?.deliveryReports ?: true

    /** The SMS manager for [subId], or the system default when [subId] is 0. */
    private fun manager(context: Context, subId: Int): SmsManager? = try {
        if (subId > 0 && subscriptionActive(context, subId)) {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    } catch (_: Exception) {
        null
    }

    /** True when [subId] is a currently active SIM; false without READ_PHONE_STATE. */
    private fun subscriptionActive(context: Context, subId: Int): Boolean = try {
        SmsSims.active(context).any { it.subId == subId }
    } catch (_: Exception) {
        false
    }
}
