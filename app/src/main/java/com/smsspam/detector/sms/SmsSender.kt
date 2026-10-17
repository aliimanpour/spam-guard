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
    const val EXTRA_RECIPIENT = "sms_recipient"
    const val EXTRA_BODY = "sms_body"
    const val EXTRA_SUB_ID = "sms_sub_id"

    /** Distinct request code per PendingIntent so reports never collide. */
    private val requestCodes = AtomicInteger(1)

    /** Splits a comma/semicolon separated recipient string into addresses. */
    fun parseRecipients(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun send(
        context: Context,
        rawRecipients: String,
        body: String,
        subId: Int = 0,
        triggerFollowUp: Boolean = true,
    ): Boolean = send(context, parseRecipients(rawRecipients), body, subId, triggerFollowUp)

    /** [sendOutcome] for a comma/semicolon separated recipient string. */
    fun sendOutcome(
        context: Context,
        rawRecipients: String,
        body: String,
        subId: Int = 0,
        triggerFollowUp: Boolean = true,
        existingRowId: Long = 0L,
    ): SendOutcome = sendOutcome(
        context,
        parseRecipients(rawRecipients),
        body,
        subId,
        triggerFollowUp,
        existingRowId,
    )

    /**
     * Sends with the SIM chosen by [subId]; 0 means the system default SIM.
     * A subscription that is no longer active (SIM swapped out) falls back to
     * the default so the message still goes out.
     *
     * When [triggerFollowUp] is true and the send succeeds, any enabled
     * follow-up workflow queues a follow-up for each recipient. Automation's
     * own sends pass false so a reply can never loop into another automation.
     */
    fun send(
        context: Context,
        recipients: List<String>,
        body: String,
        subId: Int = 0,
        triggerFollowUp: Boolean = true,
    ): Boolean = sendOutcome(context, recipients, body, subId, triggerFollowUp).ok

    /**
     * Sends and reports *why* it failed, so a caller can queue a retry with a
     * clear reason. [existingRowId] reuses an outgoing provider row written by a
     * previous attempt instead of inserting a duplicate.
     *
     * Note: "no signal" is usually reported asynchronously through the sent
     * [PendingIntent] (see [SmsStatusReceiver]) rather than here, because
     * `sendTextMessage` returns before the radio answers.
     */
    fun sendOutcome(
        context: Context,
        recipients: List<String>,
        body: String,
        subId: Int = 0,
        triggerFollowUp: Boolean = true,
        existingRowId: Long = 0L,
    ): SendOutcome {
        if (recipients.isEmpty() || body.isBlank()) return SendOutcome(false, SendFailure.UNKNOWN)
        if (airplaneModeOn(context)) return SendOutcome(false, SendFailure.AIRPLANE_MODE)
        val manager = manager(context, subId) ?: return SendOutcome(false, SendFailure.NO_SIGNAL)
        val wantReport = deliveryReportsEnabled(context)
        var failure: SendFailure? = null
        var rowIdForReuse = existingRowId
        recipients.forEach { recipient ->
            try {
                // Write the row first so the delivery/sent reports have a row id
                // to update. Null when we are not the default app (nothing to
                // update then, and reports are skipped).
                val rowId = if (rowIdForReuse > 0L) {
                    reuseRow(context, rowIdForReuse)
                    rowIdForReuse
                } else {
                    persistSent(context, recipient, body)
                }
                rowIdForReuse = rowId ?: existingRowId
                val sentIntent = statusIntent(context, recipient, rowId, ACTION_SMS_SENT, wantReport, body, subId)
                val deliveredIntent = statusIntent(context, recipient, rowId, ACTION_SMS_DELIVERED, wantReport, body, subId)
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
            } catch (e: Exception) {
                failure = classify(e)
            }
        }
        if (failure == null && triggerFollowUp) {
            (context.applicationContext as? SpamGuardApp)?.container?.let { container ->
                AutomationRunner.onOutgoing(context, container, recipients)
            }
        }
        return if (failure == null) SendOutcome.OK else SendOutcome(false, failure)
    }

    /** Resets a reused provider row so its ticks render as "sending" again. */
    private fun reuseRow(context: Context, rowId: Long) {
        if (!SmsInbox.isDefaultSmsApp(context)) return
        SmsThreads.markStatus(context, rowId, Telephony.Sms.STATUS_PENDING)
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, rowId),
                values,
                null,
                null,
            )
        } catch (_: Exception) {
            // Provider write not permitted; the message still goes to the radio.
        }
    }

    private fun classify(error: Exception): SendFailure =
        if (error is SecurityException) SendFailure.PERMISSION else SendFailure.UNKNOWN

    private fun airplaneModeOn(context: Context): Boolean = try {
        android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
    } catch (_: Exception) {
        false
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
        body: String,
        subId: Int,
    ): PendingIntent? {
        if (!enabled || rowId == null || rowId <= 0L) return null
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ROW_ID, rowId)
            putExtra(EXTRA_RECIPIENT, recipient)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_SUB_ID, subId)
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

/**
 * Why an SMS could not be handed to the radio. [retriable] failures are worth
 * queueing and retrying (they are usually transient); the rest need the user.
 */
enum class SendFailure(val message: String, val retriable: Boolean) {
    NONE("", false),
    NO_SIGNAL("No signal", true),
    AIRPLANE_MODE("Airplane mode is on", true),
    NO_SIM("No SIM", true),
    PERMISSION("Send permission is missing", false),
    UNKNOWN("Could not send", false);

    companion object {
        /**
         * Maps an [`SmsManager`](android.telephony.SmsManager) sent-report result
         * code to a reason. Called from [SmsStatusReceiver].
         */
        fun fromSentResult(code: Int): SendFailure = when (code) {
            android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> NO_SIGNAL
            android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> AIRPLANE_MODE
            android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> UNKNOWN
            else -> UNKNOWN
        }
    }
}

/** Outcome of an [SmsSender.sendOutcome] call: success, or a specific reason. */
data class SendOutcome(val ok: Boolean, val failure: SendFailure = SendFailure.NONE) {
    companion object {
        val OK = SendOutcome(true, SendFailure.NONE)
    }
}
