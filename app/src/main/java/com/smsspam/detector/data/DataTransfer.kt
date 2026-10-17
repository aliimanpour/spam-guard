package com.smsspam.detector.data

import android.content.Context
import android.net.Uri
import com.smsspam.detector.AppContainer
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.ScheduledMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs the Settings → Import/Export actions. Everything is written as a single
 * JSON document so a backup can be moved between devices or kept as a file.
 */
object DataTransfer {

    private const val FORMAT_VERSION = 1

    /** Serializes rules, blocked messages and scheduled messages to [uri]. */
    fun export(context: Context, uri: Uri, container: AppContainer): String {
        val root = JSONObject().apply {
            put("format", "spam-guard-backup")
            put("version", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put(
                "rules",
                JSONArray().apply { container.ruleRepository.rules.value.forEach { put(it.toJson()) } },
            )
            put(
                "messages",
                JSONArray().apply { container.messageRepository.messages.value.forEach { put(it.toJson()) } },
            )
            put(
                "scheduled",
                JSONArray().apply { container.scheduleRepository.scheduled.value.forEach { put(it.toJson()) } },
            )
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(root.toString(2).toByteArray())
        } ?: error("Could not open the selected file")

        val rules = container.ruleRepository.rules.value.size
        val messages = container.messageRepository.messages.value.size
        val scheduled = container.scheduleRepository.scheduled.value.size
        return "Exported $rules rules, $messages blocked and $scheduled scheduled"
    }

    /**
     * Imports a backup produced by [export]. Rules are replaced; messages and
     * scheduled messages are merged so nothing already on the device is lost.
     */
    fun import(context: Context, uri: Uri, container: AppContainer): String {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not read the selected file")
        val root = JSONObject(text)

        val rules = root.optJSONArray("rules")?.toObjects()?.map { Rule.fromJson(it) }.orEmpty()
        val messages = root.optJSONArray("messages")?.toObjects()?.map { QuarantineMessage.fromJson(it) }.orEmpty()
        val scheduled = root.optJSONArray("scheduled")?.toObjects()?.map { ScheduledMessage.fromJson(it) }.orEmpty()

        if (rules.isNotEmpty()) container.ruleRepository.save(rules)
        val messagesBefore = container.messageRepository.messages.value.size
        val messagesAfter = container.messageRepository.addAll(messages).size
        var addedScheduled = 0
        scheduled.forEach {
            if (container.scheduleRepository.scheduled.value.none { existing -> existing.id == it.id }) {
                container.scheduleRepository.add(it)
                addedScheduled++
            }
        }

        return "Imported ${rules.size} rules, ${messagesAfter - messagesBefore} blocked and $addedScheduled scheduled"
    }

    private fun JSONArray.toObjects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
