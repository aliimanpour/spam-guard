package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.model.ScheduledStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the messages queued for a scheduled send. Exposed as a [StateFlow] so
 * the UI updates live when the alarm receiver updates a message's status.
 */
class ScheduleRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_scheduled", "scheduled")

    private val _scheduled = MutableStateFlow(readFromStore())
    val scheduled: StateFlow<List<ScheduledMessage>> = _scheduled.asStateFlow()

    private fun readFromStore(): List<ScheduledMessage> =
        store.read().map { ScheduledMessage.fromJson(it) }.sortedBy { it.sendAt }

    @Synchronized
    private fun commit(list: List<ScheduledMessage>): List<ScheduledMessage> {
        val sorted = list.sortedBy { it.sendAt }
        _scheduled.value = sorted
        store.write(sorted.map { it.toJson() })
        return sorted
    }

    @Synchronized
    fun add(message: ScheduledMessage): List<ScheduledMessage> {
        if (_scheduled.value.any { it.id == message.id }) return _scheduled.value
        return commit(_scheduled.value + message)
    }

    @Synchronized
    fun update(message: ScheduledMessage): List<ScheduledMessage> =
        commit(_scheduled.value.map { if (it.id == message.id) message else it })

    @Synchronized
    fun updateStatus(id: String, status: ScheduledStatus): List<ScheduledMessage> =
        commit(_scheduled.value.map { if (it.id == id) it.copy(status = status) else it })

    @Synchronized
    fun delete(id: String): List<ScheduledMessage> =
        commit(_scheduled.value.filterNot { it.id == id })

    /** Messages still waiting to be sent (used to reschedule after a reboot). */
    fun pending(): List<ScheduledMessage> = _scheduled.value.filter { it.status == ScheduledStatus.PENDING }
}
