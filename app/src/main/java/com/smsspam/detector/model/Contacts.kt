package com.smsspam.detector.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A contact in the app's own address book: a number with a display name and
 * free-form tags used for segmentation. Persisted as JSON by
 * [com.smsspam.detector.data.ContactsRepository].
 */
data class ContactEntry(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "",
    val name: String = "",
    val tags: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("name", name)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ContactEntry = ContactEntry(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            number = o.optString("number"),
            name = o.optString("name"),
            tags = o.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }.orEmpty(),
        )
    }
}

/** A named group of contact numbers, used as a bulk-send segment. */
data class ContactGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val members: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("members", JSONArray().apply { members.forEach { put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ContactGroup = ContactGroup(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            members = o.optJSONArray("members")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }.orEmpty(),
        )
    }
}
