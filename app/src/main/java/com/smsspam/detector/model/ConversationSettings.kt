package com.smsspam.detector.model

import org.json.JSONObject

/**
 * Per-conversation preferences: notification/sound switches, pin/archive flags,
 * a lock flag and the saved draft. Keyed by the normalized phone number in
 * [com.smsspam.detector.data.ConversationSettingsRepository].
 */
data class ConversationSettings(
    val notify: Boolean = true,
    val sound: Boolean = true,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val locked: Boolean = false,
    val draft: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("notify", notify)
        put("sound", sound)
        put("pinned", pinned)
        put("archived", archived)
        put("locked", locked)
        put("draft", draft)
    }

    companion object {
        fun fromJson(o: JSONObject): ConversationSettings = ConversationSettings(
            notify = o.optBoolean("notify", true),
            sound = o.optBoolean("sound", true),
            pinned = o.optBoolean("pinned", false),
            archived = o.optBoolean("archived", false),
            locked = o.optBoolean("locked", false),
            draft = o.optString("draft"),
        )
    }
}
