package com.smsspam.detector.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Receives the per-message sent/delivery reports requested by [SmsSender] and
 * records the outcome on the outgoing provider row: a delivered report flips it
 * to `STATUS_COMPLETE` (rendered as two ticks), a failed send to `STATUS_FAILED`.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(SmsSender.EXTRA_ROW_ID, -1L)
        if (rowId <= 0L) return
        when (intent.action) {
            SmsSender.ACTION_SMS_DELIVERED ->
                SmsThreads.markStatus(context, rowId, Telephony.Sms.STATUS_COMPLETE)
            SmsSender.ACTION_SMS_SENT -> {
                if (resultCode != Activity.RESULT_OK) {
                    SmsThreads.markStatus(context, rowId, Telephony.Sms.STATUS_FAILED)
                    queueRetry(context, intent, rowId)
                }
            }
            else -> return
        }
        Log.i(TAG, "status action=${intent.action} row=$rowId result=$resultCode")
    }

    /**
     * A transient send failure (no service / radio off) is queued for an
     * automatic retry. The body and recipient are carried on the sent report so
     * the message can be rebuilt; the provider row is reused on retry so nothing
     * is duplicated.
     */
    private fun queueRetry(context: Context, intent: Intent, rowId: Long) {
        val failure = SendFailure.fromSentResult(resultCode)
        if (!failure.retriable) return
        val recipient = intent.getStringExtra(SmsSender.EXTRA_RECIPIENT).orEmpty()
        val body = intent.getStringExtra(SmsSender.EXTRA_BODY).orEmpty()
        if (recipient.isBlank() || body.isBlank()) return
        val subId = intent.getIntExtra(SmsSender.EXTRA_SUB_ID, 0)
        val queued = SendQueue.enqueue(
            context,
            recipient,
            body,
            subId,
            failure,
            attempts = 1,
            providerRowId = rowId,
        )
        Log.i(TAG, "queue retry for row=$rowId reason=${failure.message} queued=${queued?.id}")
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
