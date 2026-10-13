package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
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

    @Synchronized
    fun markRead(id: String, read: Boolean = true): List<QuarantineMessage> =
        commit(_messages.value.map { if (it.id == id) it.copy(read = read) else it })

    @Synchronized
    fun delete(id: String): List<QuarantineMessage> =
        commit(_messages.value.filterNot { it.id == id })

    @Synchronized
    fun clearAll(): List<QuarantineMessage> = commit(emptyList())
}
