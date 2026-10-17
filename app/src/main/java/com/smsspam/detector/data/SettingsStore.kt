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

    companion object {
        private const val PREFS_NAME = "spam_guard_settings"
        private const val KEY_AUTO_UNSUBSCRIBE = "auto_unsubscribe"
        private const val KEY_UNSUBSCRIBE_CODE = "unsubscribe_code"
        private const val KEY_DELIVERY_REPORTS = "delivery_reports"

        const val CODE_AUTO = "auto"
        val CODE_CHOICES = listOf(CODE_AUTO, "11", "12")
    }
}
