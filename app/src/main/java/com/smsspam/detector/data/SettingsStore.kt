package com.smsspam.detector.data

import android.content.Context

/**
 * App-wide toggles that are not lists, persisted in SharedPreferences.
 * Currently hosts the auto-unsubscribe feature.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When true, a blocked subscription ad is answered with its unsubscribe code. */
    var autoUnsubscribe: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UNSUBSCRIBE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UNSUBSCRIBE, value).apply()

    /**
     * Which code to send: "auto" replies with the code the ad itself mentions
     * (لغو11 → 11, لغو12 → 12); "11" / "12" force that fixed code.
     */
    var unsubscribeCode: String
        get() = prefs.getString(KEY_UNSUBSCRIBE_CODE, CODE_AUTO) ?: CODE_AUTO
        set(value) = prefs.edit().putString(KEY_UNSUBSCRIBE_CODE, value).apply()

    /**
     * When true (the default), outgoing messages request a delivery report and
     * show a second tick once the report confirms delivery.
     */
    var deliveryReports: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_REPORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_DELIVERY_REPORTS, value).apply()

    /** Theme mode id: [THEME_SYSTEM] (default), [THEME_LIGHT] or [THEME_DARK]. */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /**
     * When true, dark themes paint a true-black background for AMOLED screens.
     * Has no effect on light themes.
     */
    var amoledDark: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_DARK, value).apply()

    /** Color palette id: [PALETTE_DYNAMIC] (default) or one of [PALETTE_CHOICES]. */
    var palette: String
        get() = prefs.getString(KEY_PALETTE, PALETTE_DYNAMIC) ?: PALETTE_DYNAMIC
        set(value) = prefs.edit().putString(KEY_PALETTE, value).apply()

    /**
     * When true, messages from numbers that are not saved contacts are not
     * shown as notifications.
     */
    var hideUnknownNotifications: Boolean
        get() = prefs.getBoolean(KEY_HIDE_UNKNOWN_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_UNKNOWN_NOTIFICATIONS, value).apply()

    /**
     * When true (the default), a message that fails with no signal is queued and
     * retried automatically with a growing backoff.
     */
    var autoRetry: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RETRY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RETRY, value).apply()

    /**
     * When true (the default), the conversation offers on-device reply
     * suggestions for the last incoming message.
     */
    var smartReplies: Boolean
        get() = prefs.getBoolean(KEY_SMART_REPLIES, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_REPLIES, value).apply()

    companion object {
        private const val PREFS_NAME = "spam_guard_settings"
        private const val KEY_AUTO_UNSUBSCRIBE = "auto_unsubscribe"
        private const val KEY_UNSUBSCRIBE_CODE = "unsubscribe_code"
        private const val KEY_DELIVERY_REPORTS = "delivery_reports"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED_DARK = "amoled_dark"
        private const val KEY_PALETTE = "palette"
        private const val KEY_HIDE_UNKNOWN_NOTIFICATIONS = "hide_unknown_notifications"
        private const val KEY_AUTO_RETRY = "auto_retry"
        private const val KEY_SMART_REPLIES = "smart_replies"

        const val CODE_AUTO = "auto"
        val CODE_CHOICES = listOf(CODE_AUTO, "11", "12")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val PALETTE_DYNAMIC = "dynamic"
    }
}
