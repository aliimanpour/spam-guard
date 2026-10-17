package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.AutoReply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the keyword auto-replies. Exposed as a [StateFlow] so the receiver and
 * the UI share one source of truth.
 */
class AutoReplyRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_auto_replies", "replies")

    private val _replies = MutableStateFlow(readFromStore())
    val replies: StateFlow<List<AutoReply>> = _replies.asStateFlow()

    private fun readFromStore(): List<AutoReply> = store.read().map { AutoReply.fromJson(it) }

    @Synchronized
    private fun commit(list: List<AutoReply>): List<AutoReply> {
        _replies.value = list
        store.write(list.map { it.toJson() })
        return list
    }

    /** Inserts or updates [reply] by id. */
    @Synchronized
    fun save(reply: AutoReply): List<AutoReply> {
        val current = _replies.value.toMutableList()
        val index = current.indexOfFirst { it.id == reply.id }
        if (index >= 0) current[index] = reply else current.add(reply)
        return commit(current)
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): List<AutoReply> =
        commit(_replies.value.map { if (it.id == id) it.copy(enabled = enabled) else it })

    @Synchronized
    fun delete(id: String): List<AutoReply> = commit(_replies.value.filterNot { it.id == id })
}
