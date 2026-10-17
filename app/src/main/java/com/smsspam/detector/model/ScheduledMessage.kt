package com.smsspam.detector.model

import org.json.JSONObject
import java.util.UUID

/** Lifecycle of a message the user queued for a later send time. */
enum class ScheduledStatus(val label: String) {
    PENDING("Scheduled"),
    SENT("Sent"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

/**
 * An SMS the user wants to send at a future time, or one that failed with no
 * signal and is waiting to be retried. Persisted as JSON in SharedPreferences
 * (see [com.smsspam.detector.data.ScheduleRepository]) and delivered by
 * [com.smsspam.detector.sms.SmsAlarmReceiver].
 */
data class ScheduledMessage(
    val id: String = UUID.randomUUID().toString(),
    val recipient: String = "",
    val body: String = "",
    val sendAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: ScheduledStatus = ScheduledStatus.PENDING,
    /** Subscription id of the SIM to send with; 0 = system default SIM. */
    val subId: Int = 0,
    /** How many times delivery has been attempted (0 = not tried yet). */
    val attempts: Int = 0,
    /** Why the last attempt failed, for display ("No signal", …). */
    val lastError: String = "",
    /** Provider row to reuse on retry so the message is not duplicated. */
    val providerRowId: Long = 0L,
) {
    /** Stable request code used for the PendingIntent / alarm slot. */
    val requestCode: Int get() = id.hashCode()

    /** True when this entry is an automatic retry of a message we tried to send. */
    val queued: Boolean get() = attempts > 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("recipient", recipient)
        put("body", body)
        put("sendAt", sendAt)
        put("createdAt", createdAt)
        put("status", status.name)
        put("subId", subId)
        put("attempts", attempts)
        put("lastError", lastError)
        put("providerRowId", providerRowId)
    }

    companion object {
        fun fromJson(o: JSONObject): ScheduledMessage = ScheduledMessage(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            recipient = o.optString("recipient"),
            body = o.optString("body"),
            sendAt = o.optLong("sendAt", System.currentTimeMillis()),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            status = enumValues<ScheduledStatus>().firstOrNull { it.name == o.optString("status") }
                ?: ScheduledStatus.PENDING,
            subId = o.optInt("subId", 0),
            attempts = o.optInt("attempts", 0),
            lastError = o.optString("lastError"),
            providerRowId = o.optLong("providerRowId", 0L),
        )
    }
}
