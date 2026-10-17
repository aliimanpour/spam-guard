package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.PendingFollowUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follow-ups waiting to fire: created after an outgoing send, cancelled when
 * the contact replies. Exposed as a [StateFlow] so the UI can show them.
 */
class FollowUpRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_followups", "followups")

    private val _pending = MutableStateFlow(readFromStore())
    val pending: StateFlow<List<PendingFollowUp>> = _pending.asStateFlow()

    private fun readFromStore(): List<PendingFollowUp> =
        store.read().map { PendingFollowUp.fromJson(it) }.sortedBy { it.dueAt }

    @Synchronized
    private fun commit(list: List<PendingFollowUp>): List<PendingFollowUp> {
        val sorted = list.sortedBy { it.dueAt }
        _pending.value = sorted
        store.write(sorted.map { it.toJson() })
        return sorted
    }

    @Synchronized
    fun add(item: PendingFollowUp): List<PendingFollowUp> {
        if (_pending.value.any { it.id == item.id }) return _pending.value
        return commit(_pending.value + item)
    }

    @Synchronized
    fun delete(id: String): List<PendingFollowUp> =
        commit(_pending.value.filterNot { it.id == id })

    /** Removes and returns the follow-ups queued for [recipient] (they replied). */
    @Synchronized
    fun removeFor(recipient: String): List<PendingFollowUp> {
        val key = normalize(recipient)
        val (matching, remaining) = _pending.value.partition { normalize(it.recipient) == key }
        if (matching.isNotEmpty()) commit(remaining)
        return matching
    }

    /** Removes every pending follow-up for a workflow (it was edited or deleted). */
    @Synchronized
    fun removeForWorkflow(workflowId: String): List<PendingFollowUp> {
        val (matching, remaining) = _pending.value.partition { it.workflowId == workflowId }
        if (matching.isNotEmpty()) commit(remaining)
        return matching
    }

    fun all(): List<PendingFollowUp> = _pending.value

    private fun normalize(raw: String): String =
        raw.filter { it.isDigit() }.trimStart('0').takeLast(10)
}
