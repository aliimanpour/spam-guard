package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the messages caught by the spam filter.
 *
 * The list is exposed as a [StateFlow] so the UI stays in sync even when messages
 * are added by the SMS receiver while the app is running.
 */
class MessageRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_messages", "messages")

    private val _messages = MutableStateFlow(readFromStore())
    val messages: StateFlow<List<QuarantineMessage>> = _messages.asStateFlow()

    private fun readFromStore(): List<QuarantineMessage> =
        store.read().map { QuarantineMessage.fromJson(it) }.sortedByDescending { it.timestamp }

    @Synchronized
    private fun commit(list: List<QuarantineMessage>): List<QuarantineMessage> {
        val sorted = list.sortedByDescending { it.timestamp }
        _messages.value = sorted
        store.write(sorted.map { it.toJson() })
        return sorted
    }

    /** Inserts a message unless an identical one already exists. */
    @Synchronized
    fun add(message: QuarantineMessage): List<QuarantineMessage> {
        if (_messages.value.any { it.dedupeKey == message.dedupeKey }) return _messages.value
        return commit(_messages.value + message)
    }

    @Synchronized
    fun addAll(messages: List<QuarantineMessage>): List<QuarantineMessage> {
        val keys = _messages.value.map { it.dedupeKey }.toMutableSet()
        val fresh = messages.filter { keys.add(it.dedupeKey) }
        if (fresh.isEmpty()) return _messages.value
        return commit(_messages.value + fresh)
    }

    @Synchronized
    fun updateStatus(id: String, status: QuarantineStatus): List<QuarantineMessage> =
        commit(_messages.value.map { if (it.id == id) it.copy(status = status) else it })

    /** Applies per-message statuses in a single write (used by bulk actions). */
    @Synchronized
    fun updateStatuses(statuses: Map<String, QuarantineStatus>): List<QuarantineMessage> =
        commit(
            _messages.value.map { message ->
                statuses[message.id]?.let { message.copy(status = it) } ?: message
            }
        )

    @Synchronized
    fun markRead(id: String, read: Boolean = true): List<QuarantineMessage> =
        commit(_messages.value.map { if (it.id == id) it.copy(read = read) else it })

    @Synchronized
    fun delete(id: String): List<QuarantineMessage> =
        commit(_messages.value.filterNot { it.id == id })

    /** Deletes several messages in a single write (used by multi-select). */
    @Synchronized
    fun deleteAll(ids: Set<String>): List<QuarantineMessage> =
        commit(_messages.value.filterNot { it.id in ids })

    @Synchronized
    fun clearAll(): List<QuarantineMessage> = commit(emptyList())

    /**
     * Re-checks messages that were caught by a preset that is no longer shipped
     * (see [RuleRepository.RETIRED_PRESET_IDS]) against the live [rules]. A
     * message that no longer matches any enabled block rule — the old link-only
     * filter caught every message containing a URL, for example — is marked
     * not-spam so it leaves the Blocked tab instead of staying a false positive
     * forever. Messages that still match a current rule are left untouched.
     *
     * Idempotent: once a message is un-blocked it is no longer re-evaluated.
     * Returns the number of messages freed.
     */
    @Synchronized
    fun reconcileRetiredMatches(retiredRuleIds: Set<String>, rules: List<Rule>): Int {
        if (retiredRuleIds.isEmpty()) return 0
        var freed = 0
        val updated = _messages.value.map { message ->
            if (message.isBlocked &&
                message.matchedRuleId in retiredRuleIds &&
                RuleEngine.firstMatch(message.sender, message.body, rules) == null
            ) {
                freed++
                message.copy(status = QuarantineStatus.NOT_SPAM)
            } else {
                message
            }
        }
        if (freed > 0) commit(updated)
        return freed
    }
}
