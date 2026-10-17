package com.smsspam.detector.model

import org.json.JSONObject
import java.util.UUID

/** What starts an automated workflow. */
enum class WorkflowTrigger(val label: String) {
    /** Fires the first time a contact ever messages you. */
    WELCOME("Welcome · first message"),

    /** Fires when an incoming message contains the keyword. */
    KEYWORD("Keyword in a message"),

    /** Fires at a chosen time (a one-shot reminder). */
    REMINDER("Reminder at a time"),

    /** Fires when you message someone and they do not reply in time. */
    FOLLOW_UP("Follow-up if no reply"),
}

/** Who a workflow sends to. */
enum class WorkflowTarget(val label: String) {
    /** Reply to the sender that triggered the workflow. */
    SENDER("The sender"),

    /** A comma-separated list of numbers. */
    NUMBERS("Specific numbers"),

    /** Everyone in a saved group. */
    GROUP("A group"),

    /** Everyone tagged with a label. */
    TAG("Contacts with a tag"),
}

/**
 * An automated workflow: a trigger plus the message to send. Welcome, keyword
 * and follow-up workflows reply to the sender; reminders send to a chosen
 * target. Persisted as JSON by
 * [com.smsspam.detector.data.WorkflowRepository].
 */
data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: WorkflowTrigger = WorkflowTrigger.WELCOME,
    /** Keyword for [WorkflowTrigger.KEYWORD]. */
    val keyword: String = "",
    /** Epoch millis for [WorkflowTrigger.REMINDER]. */
    val triggerAt: Long = 0L,
    /** Minutes to wait before a [WorkflowTrigger.FOLLOW_UP]. */
    val followUpDelayMinutes: Int = 60,
    val target: WorkflowTarget = WorkflowTarget.SENDER,
    /** Numbers (comma separated) / group id / tag, depending on [target]. */
    val targetValue: String = "",
    val message: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("trigger", trigger.name)
        put("keyword", keyword)
        put("triggerAt", triggerAt)
        put("followUpDelayMinutes", followUpDelayMinutes)
        put("target", target.name)
        put("targetValue", targetValue)
        put("message", message)
    }

    companion object {
        fun fromJson(o: JSONObject): Workflow = Workflow(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            trigger = enumValues<WorkflowTrigger>().firstOrNull { it.name == o.optString("trigger") }
                ?: WorkflowTrigger.WELCOME,
            keyword = o.optString("keyword"),
            triggerAt = o.optLong("triggerAt", 0L),
            followUpDelayMinutes = o.optInt("followUpDelayMinutes", 60),
            target = enumValues<WorkflowTarget>().firstOrNull { it.name == o.optString("target") }
                ?: WorkflowTarget.SENDER,
            targetValue = o.optString("targetValue"),
            message = o.optString("message"),
        )
    }
}

/** A follow-up waiting to fire unless the contact replies first. */
data class PendingFollowUp(
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val recipient: String,
    val message: String,
    val dueAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("workflowId", workflowId)
        put("recipient", recipient)
        put("message", message)
        put("dueAt", dueAt)
    }

    companion object {
        fun fromJson(o: JSONObject): PendingFollowUp = PendingFollowUp(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            workflowId = o.optString("workflowId"),
            recipient = o.optString("recipient"),
            message = o.optString("message"),
            dueAt = o.optLong("dueAt", System.currentTimeMillis()),
        )
    }
}
