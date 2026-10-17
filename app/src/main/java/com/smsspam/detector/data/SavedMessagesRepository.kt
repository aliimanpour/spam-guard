package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.SavedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the user's "Saved messages" — personal notes kept on device. Exposed
 * as a [StateFlow] so the Saved messages conversation and the pinned row in the
 * conversation list update live.
 */
class SavedMessagesRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_saved", "messages")

    private val _messages = MutableStateFlow(readFromStore())
    val messages: StateFlow<List<SavedMessage>> = _messages.asStateFlow()

    private fun readFromStore(): List<SavedMessage> =
        store.read().map { SavedMessage.fromJson(it) }.sortedBy { it.timestamp }

    @Synchronized
    private fun commit(list: List<SavedMessage>): List<SavedMessage> {
        val sorted = list.sortedBy { it.timestamp }
        _messages.value = sorted
        store.write(sorted.map { it.toJson() })
        return sorted
    }

    @Synchronized
    fun add(message: SavedMessage): List<SavedMessage> {
        if (_messages.value.any { it.id == message.id }) return _messages.value
        return commit(_messages.value + message)
    }

    @Synchronized
    fun delete(id: String): List<SavedMessage> =
        commit(_messages.value.filterNot { it.id == id })

    /** Deletes several saved messages in a single write. */
    @Synchronized
    fun deleteAll(ids: Set<String>): List<SavedMessage> =
        commit(_messages.value.filterNot { it.id in ids })

    @Synchronized
    fun clearAll(): List<SavedMessage> = commit(emptyList())
}
