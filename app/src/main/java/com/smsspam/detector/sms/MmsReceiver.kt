package com.smsspam.detector.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Receives WAP push (MMS) delivery for the default SMS app.
 *
 * The platform requires the default SMS app to declare this component before the
 * app can be assigned the SMS role. Spam Guard focuses on SMS spam filtering, so
 * MMS content is not downloaded here — this receiver exists to satisfy the role
 * requirement and to avoid dropping the broadcast silently without a trace.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        Log.i(TAG, "MMS WAP push received; Spam Guard does not download MMS content.")
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
