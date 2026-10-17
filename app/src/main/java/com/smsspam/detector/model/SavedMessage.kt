package com.smsspam.detector.model

import org.json.JSONObject
import java.util.UUID

/**
 * A note the user saved to themselves, Telegram-style. Saved messages never
 * leave the device: they are stored locally and are not sent as SMS.
 * Persisted as JSON in SharedPreferences by
 * [com.smsspam.detector.data.SavedMessagesRepository].
 */
data class SavedMessage(
    val id: String = UUID.randomUUID().toString(),
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("body", body)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject): SavedMessage = SavedMessage(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            body = o.optString("body"),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        )
    }
}
