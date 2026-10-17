package com.smsspam.detector.data

import android.content.Context

/**
 * Small bookkeeping store for the automation features, kept in
 * SharedPreferences:
 *  - which senders have ever messaged us (so a "welcome" workflow only fires
 *    for a first contact), and
 *  - which auto-replies already answered a sender (so a rule set to reply once
 *    per sender never loops).
 *
 * Both sets are bounded so the prefs file cannot grow without limit.
 */
class AutomationLog(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val seenSenders: LinkedHashSet<String> =
        LinkedHashSet(prefs.getStringSet(KEY_SEEN_SENDERS, emptySet()).orEmpty())

    private val replied: LinkedHashSet<String> =
        LinkedHashSet(prefs.getStringSet(KEY_REPLIED, emptySet()).orEmpty())

    @Synchronized
    fun hasSeenSender(sender: String): Boolean = normalize(sender) in seenSenders

    @Synchronized
    fun markSenderSeen(sender: String) {
        val key = normalize(sender)
        if (key.isEmpty() || !seenSenders.add(key)) return
        trim(seenSenders)
        prefs.edit().putStringSet(KEY_SEEN_SENDERS, HashSet(seenSenders)).apply()
    }

    @Synchronized
    fun alreadyReplied(replyId: String, sender: String): Boolean =
        replyKey(replyId, sender) in replied

    @Synchronized
    fun recordReply(replyId: String, sender: String) {
        if (!replied.add(replyKey(replyId, sender))) return
        trim(replied)
        prefs.edit().putStringSet(KEY_REPLIED, HashSet(replied)).apply()
    }

    private fun replyKey(replyId: String, sender: String) = "$replyId|${normalize(sender)}"

    private fun trim(set: LinkedHashSet<String>) {
        while (set.size > MAX_ENTRIES) set.remove(set.first())
    }

    private fun normalize(raw: String): String =
        raw.filter { it.isDigit() }.trimStart('0').takeLast(10).ifEmpty { raw.trim().lowercase() }

    private companion object {
        const val PREFS_NAME = "spam_guard_automation"
        const val KEY_SEEN_SENDERS = "seen_senders"
        const val KEY_REPLIED = "replied"
        const val MAX_ENTRIES = 1000
    }
}
