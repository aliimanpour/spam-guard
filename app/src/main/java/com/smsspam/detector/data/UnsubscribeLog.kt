package com.smsspam.detector.data

import android.content.Context

/**
 * Remembers which senders already received an unsubscribe SMS, so auto
 * unsubscribe fires at most once per sender. Bounded to keep the prefs file
 * small; oldest entries are dropped first.
 */
class UnsubscribeLog(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val senders: LinkedHashSet<String> = LinkedHashSet(
        prefs.getStringSet(KEY_SENDERS, emptySet()).orEmpty(),
    )

    @Synchronized
    fun alreadySent(sender: String): Boolean = sender in senders

    @Synchronized
    fun record(sender: String) {
        senders.add(sender)
        while (senders.size > MAX_SENDERS) {
            senders.remove(senders.first())
        }
        prefs.edit().putStringSet(KEY_SENDERS, HashSet(senders)).apply()
    }

    private companion object {
        const val PREFS_NAME = "spam_guard_unsubscribed"
        const val KEY_SENDERS = "senders"
        const val MAX_SENDERS = 500
    }
}
