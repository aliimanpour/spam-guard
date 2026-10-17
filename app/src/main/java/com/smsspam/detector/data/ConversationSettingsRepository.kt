package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.contacts.ContactsResolver
import com.smsspam.detector.model.ConversationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Per-conversation preferences stored as one JSON map in SharedPreferences:
 * notification/sound switches, pin/archive flags, a lock flag and drafts.
 * Exposed as a [StateFlow] so the list and the open thread stay in sync.
 */
class ConversationSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Map<String, ConversationSettings>> = _settings.asStateFlow()

    private fun read(): Map<String, ConversationSettings> {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    obj.optJSONObject(key)?.let { put(key, ConversationSettings.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Settings for [address], or defaults when nothing is stored. */
    fun get(address: String): ConversationSettings =
        _settings.value[key(address)] ?: ConversationSettings()

    fun isPinned(address: String): Boolean = get(address).pinned
    fun isArchived(address: String): Boolean = get(address).archived
    fun isLocked(address: String): Boolean = get(address).locked
    fun draft(address: String): String = get(address).draft

    @Synchronized
    fun update(address: String, transform: (ConversationSettings) -> ConversationSettings): ConversationSettings {
        val key = key(address)
        val updated = transform(_settings.value[key] ?: ConversationSettings())
        val next = _settings.value.toMutableMap().apply { put(key, updated) }
        _settings.value = next
        persist(next)
        return updated
    }

    fun setNotify(address: String, enabled: Boolean) = update(address) { it.copy(notify = enabled) }
    fun setSound(address: String, enabled: Boolean) = update(address) { it.copy(sound = enabled) }
    fun setPinned(address: String, pinned: Boolean) = update(address) { it.copy(pinned = pinned) }
    fun setArchived(address: String, archived: Boolean) = update(address) { it.copy(archived = archived) }
    fun setLocked(address: String, locked: Boolean) = update(address) { it.copy(locked = locked) }
    fun setDraft(address: String, draft: String) = update(address) { it.copy(draft = draft) }
    fun clearDraft(address: String) = update(address) { it.copy(draft = "") }

    @Synchronized
    fun remove(address: String) {
        val key = key(address)
        if (key !in _settings.value) return
        val next = _settings.value.toMutableMap().apply { remove(key) }
        _settings.value = next
        persist(next)
    }

    private fun persist(map: Map<String, ConversationSettings>) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value.toJson()) }
        prefs.edit().putString(KEY_SETTINGS, obj.toString()).apply()
    }

    private fun key(address: String): String {
        val normalized = ContactsResolver.normalize(address)
        return normalized.ifBlank { address.trim() }
    }

    private companion object {
        const val PREFS_NAME = "spam_guard_conversation_settings"
        const val KEY_SETTINGS = "settings"
    }
}
