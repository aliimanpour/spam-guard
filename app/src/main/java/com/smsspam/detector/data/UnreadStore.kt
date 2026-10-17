package com.smsspam.detector.data

import android.content.Context

/**
 * Remembers the moment after which a blocked message counts as unread: the last
 * time the user opened the Blocked tab or exited the app. The Blocked-tab badge
 * counts blocked messages newer than that.
 *
 * One persisted timestamp keeps the whole behavior: the badge resets to zero
 * when the app is exited, while messages that arrived while the app was closed
 * still count as unread on the next launch.
 */
class UnreadStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // First launch: seed with "now" so pre-existing history does not badge.
        if (!prefs.contains(KEY_LAST_SEEN_AT)) {
            prefs.edit().putLong(KEY_LAST_SEEN_AT, System.currentTimeMillis()).apply()
        }
    }

    /** Epoch millis after which a blocked message counts as unread. */
    fun lastSeenAt(): Long = prefs.getLong(KEY_LAST_SEEN_AT, 0L)

    /** Marks this moment as seen; blocked messages older than it no longer badge. */
    fun markSeen(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SEEN_AT, at).apply()
    }

    private companion object {
        const val PREFS_NAME = "spam_guard_unread"
        const val KEY_LAST_SEEN_AT = "blocked_last_seen_at"
    }
}
