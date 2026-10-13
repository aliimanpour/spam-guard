package com.smsspam.detector.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny persistence helper that stores a list of JSON objects as a string inside
 * [android.content.SharedPreferences]. Keeps the app dependency-free while still
 * giving us structured storage for rules and caught messages.
 */
internal class JsonListStore(context: Context, fileName: String, private val key: String) {

    private val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun write(items: List<JSONObject>) {
        val array = JSONArray().apply { items.forEach { put(it) } }
        prefs.edit { putString(key, array.toString()) }
    }
}
