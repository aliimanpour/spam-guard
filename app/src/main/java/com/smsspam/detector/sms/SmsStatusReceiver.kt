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
                }
            }
            else -> return
        }
        Log.i(TAG, "status action=${intent.action} row=$rowId result=$resultCode")
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
