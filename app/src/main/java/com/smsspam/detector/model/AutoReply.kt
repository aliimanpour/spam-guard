package com.smsspam.detector.model

import org.json.JSONObject
import java.util.UUID

/** How an auto-reply decides whether an incoming message should be answered. */
enum class ReplyTrigger(val label: String) {
    /** Every incoming message gets the reply. */
    ANY("Any message"),

    /** The body contains the pattern (case-insensitive). */
    CONTAINS("Contains"),

    /** The trimmed body equals the pattern. */
    EXACT("Equals"),

    /** The body matches the pattern as a regular expression. */
    REGEX("Matches regex"),
}

/**
 * A keyword response: when an incoming message matches, the [response] is sent
 * back to the sender automatically. Persisted as JSON by
 * [com.smsspam.detector.data.AutoReplyRepository].
 */
data class AutoReply(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: ReplyTrigger = ReplyTrigger.CONTAINS,
    val pattern: String = "",
    val response: String = "",
    /** When set, only messages from this address get the reply; blank = anyone. */
    val senderFilter: String = "",
    /** Reply at most once per sender, which also guards against reply loops. */
    val oncePerSender: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("trigger", trigger.name)
        put("pattern", pattern)
        put("response", response)
        put("senderFilter", senderFilter)
        put("oncePerSender", oncePerSender)
    }

    companion object {
        fun fromJson(o: JSONObject): AutoReply = AutoReply(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            trigger = enumValues<ReplyTrigger>().firstOrNull { it.name == o.optString("trigger") }
                ?: ReplyTrigger.CONTAINS,
            pattern = o.optString("pattern"),
            response = o.optString("response"),
            senderFilter = o.optString("senderFilter"),
            oncePerSender = o.optBoolean("oncePerSender", true),
        )
    }
}
