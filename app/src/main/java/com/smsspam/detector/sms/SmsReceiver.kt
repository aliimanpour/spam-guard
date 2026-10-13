package com.smsspam.detector.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.notify.Notifier

/**
 * Receives SMS and applies the user's rules.
 *
 * There are two delivery paths:
 *  - [Telephony.Sms.Intents.SMS_DELIVER_ACTION] is sent to the **default SMS app**.
 *    When we hold the role we are responsible for persisting messages, so normal
 *    messages are written to the system inbox and caught messages are kept only in
 *    the app quarantine.
 *  - [Telephony.Sms.Intents.SMS_RECEIVED_ACTION] is the normal broadcast, still
 *    handled (best effort) when we are *not* the default app.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val isReceived = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!isDeliver && !isReceived) return

        val app = context.applicationContext as? SpamGuardApp ?: return
        val container = app.container

        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (_: Exception) {
            null
        } ?: return

        val isDefault = SmsInbox.isDefaultSmsApp(context)
        Log.i(TAG, "SMS broadcast action=$action isDefault=$isDefault parts=${parts.size}")

        // When we hold the default role, SMS_DELIVER is authoritative; ignore
        // SMS_RECEIVED so a message is never processed twice.
        if (isReceived && isDefault) return

        val rules = container.ruleRepository.rules.value

        // Multipart messages arrive as several PDUs from the same sender.
        val bySender = parts.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "Unknown" }
        bySender.forEach { (sender, senderParts) ->
            val body = senderParts.joinToString(separator = "") {
                it.displayMessageBody ?: it.messageBody ?: ""
            }
            val timestamp = senderParts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

            val match = RuleEngine.firstMatch(sender, body, rules)
            if (match != null) {
                val message = SmsInbox.toQuarantine(
                    InboxSms(id = 0L, sender = sender, body = body, timestamp = timestamp),
                    ruleId = match.id,
                    ruleName = match.name,
                )
                container.messageRepository.add(message)
                // Always try to remove the message from the system inbox. This only
                // succeeds when we are the default SMS app (or hold WRITE_SMS).
                val deleted = SmsInbox.deleteMatching(context, sender, body, timestamp)
                Log.i(TAG, "blocked spam from=$sender rule='${match.name}' removedFromInbox=$deleted")
                Notifier.notifySpam(context, message)
            } else if (isDeliver) {
                // We are the default SMS app, so we must ensure normal messages are
                // stored, otherwise the user would lose them.
                val alreadyStored = SmsInbox.exists(context, sender, body, timestamp)
                val stored = alreadyStored || SmsInbox.insertIncoming(context, sender, body, timestamp)
                Log.i(TAG, "allowed message from=$sender alreadyStored=$alreadyStored stored=$stored")
                Notifier.notifyMessage(context, sender, body)
            } else {
                Log.i(TAG, "allowed message from=$sender (not default app; left as is)")
            }
        }
    }

    private companion object {
        const val TAG = "SpamGuard"
    }
}
