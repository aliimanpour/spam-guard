package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.Workflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the automated workflows (welcome, keyword, reminder, follow-up).
 * Exposed as a [StateFlow] so the receiver, the alarm worker and the UI share
 * one source of truth.
 */
class WorkflowRepository(context: Context) {

    private val store = JsonListStore(context, "spam_guard_workflows", "workflows")

    private val _workflows = MutableStateFlow(readFromStore())
    val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

    private fun readFromStore(): List<Workflow> = store.read().map { Workflow.fromJson(it) }

    @Synchronized
    private fun commit(list: List<Workflow>): List<Workflow> {
        _workflows.value = list
        store.write(list.map { it.toJson() })
        return list
    }

    /** Inserts or updates [workflow] by id. */
    @Synchronized
    fun save(workflow: Workflow): List<Workflow> {
        val current = _workflows.value.toMutableList()
        val index = current.indexOfFirst { it.id == workflow.id }
        if (index >= 0) current[index] = workflow else current.add(workflow)
        return commit(current)
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): List<Workflow> =
        commit(_workflows.value.map { if (it.id == id) it.copy(enabled = enabled) else it })

    @Synchronized
    fun delete(id: String): List<Workflow> = commit(_workflows.value.filterNot { it.id == id })

    fun enabled(): List<Workflow> = _workflows.value.filter { it.enabled }

    fun byId(id: String): Workflow? = _workflows.value.firstOrNull { it.id == id }
}
