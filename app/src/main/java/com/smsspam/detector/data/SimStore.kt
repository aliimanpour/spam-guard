package com.smsspam.detector.data

import android.content.Context

/**
 * Remembers which SIM (subscription id) the user picked for each conversation
 * address, so the picker preselects it the next time the thread opens.
 */
class SimStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stored subscription id for [address]; 0 when the conversation uses the default SIM. */
    fun subIdFor(address: String): Int = prefs.getInt(key(address), 0)

    fun setSubId(address: String, subId: Int) {
        prefs.edit().putInt(key(address), subId).apply()
    }

    private fun key(address: String) = "sim|$address"

    private companion object {
        const val PREFS_NAME = "spam_guard_sims"
    }
}
