package com.smsspam.detector.model

import org.json.JSONObject
import java.util.UUID

/** Lifecycle of a message that was caught by the spam filter. */
enum class QuarantineStatus {
    /** Caught by a rule and not yet reviewed. */
    SPAM,

    /** User confirmed it was spam / it stays filtered. */
    CONFIRMED_SPAM,

    /** User said it was safe; kept here so nothing is lost. */
    NOT_SPAM,

    /** User restored it. */
    RESTORED,
}

/** A message that was moved out of the system SMS app and kept here. */
data class QuarantineMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String = "",
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val matchedRuleId: String? = null,
    val matchedRuleName: String? = null,
    val status: QuarantineStatus = QuarantineStatus.SPAM,
    val read: Boolean = false,
) {
    /** Stable key used to avoid importing the same inbox message twice. */
    val dedupeKey: String get() = "$sender|$timestamp|$body"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sender", sender)
        put("body", body)
        put("timestamp", timestamp)
        put("matchedRuleId", matchedRuleId ?: JSONObject.NULL)
        put("matchedRuleName", matchedRuleName ?: JSONObject.NULL)
        put("status", status.name)
        put("read", read)
    }

    companion object {
        fun fromJson(o: JSONObject): QuarantineMessage = QuarantineMessage(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            sender = o.optString("sender"),
            body = o.optString("body"),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            matchedRuleId = if (o.isNull("matchedRuleId")) null else o.optString("matchedRuleId"),
            matchedRuleName = if (o.isNull("matchedRuleName")) null else o.optString("matchedRuleName"),
            status = enumValues<QuarantineStatus>().firstOrNull { it.name == o.optString("status") }
                ?: QuarantineStatus.SPAM,
            read = o.optBoolean("read", false),
        )
    }
}
