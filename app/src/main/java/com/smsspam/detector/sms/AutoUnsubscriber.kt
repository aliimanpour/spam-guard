package com.smsspam.detector.sms

import android.content.Context
import android.util.Log
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.data.SettingsStore

/**
 * Replies "11" (or the code the ad asks for) to the sender of a blocked
 * subscription ad, at most once per sender, when the user enabled auto
 * unsubscribe in Settings.
 *
 * The feature is intentionally conservative: a code is only sent when the
 * blocked message itself carries a لغو unsubscribe hint, so unrelated blocked
 * senders never receive anything.
 */
object AutoUnsubscriber {

    private const val TAG = "SpamGuard"

    /** لغو followed by a one/two-digit code, Latin or Persian digits. */
    private val unsubscribeHint = Regex("لغو\\s*[\\d۰-۹٠-٩]{1,2}")

    /**
     * Sends the unsubscribe code to [sender] when needed. Returns the code that
     * was sent, or null when nothing was (disabled, no hint, already sent).
     */
    fun maybeUnsubscribe(context: Context, sender: String, body: String): String? {
        val app = context.applicationContext as? SpamGuardApp ?: return null
        val settings = app.container.settingsStore
        if (!settings.autoUnsubscribe || sender.isBlank()) return null
        val code = resolveCode(settings.unsubscribeCode, body) ?: return null
        val log = app.container.unsubscribeLog
        if (log.alreadySent(sender)) return null
        val sent = SmsSender.send(context, sender, code, triggerFollowUp = false)
        Log.i(TAG, "auto unsubscribe to=$sender code=$code sent=$sent")
        return code.takeIf { sent }?.also { log.record(sender) }
    }

    /**
     * The code to send: the user's fixed choice, or — for "auto" — the digits
     * found after لغو in the ad itself, falling back to 11.
     */
    private fun resolveCode(setting: String, body: String): String? {
        if (setting != SettingsStore.CODE_AUTO) return setting
        val normalized = buildString(body.length) {
            body.forEach { ch ->
                when (ch) {
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    else -> append(ch)
                }
            }
        }
        val digits = unsubscribeHint.find(normalized)?.value?.filter { it.isDigit() }
        // Trust only the conventional codes; anything else falls back to 11.
        return if (digits != null && digits.length <= 2 && digits.toInt() in 11..14) digits else "11"
    }
}
