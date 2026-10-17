package com.smsspam.detector.contacts

import android.content.Context
import android.provider.ContactsContract

/**
 * Resolves phone numbers to contact display names for the conversations UI.
 * All calls are best effort: without READ_CONTACTS, or when the provider is
 * unavailable, they return empty results so the UI falls back to the number.
 */
object ContactsResolver {

    /**
     * Reduces a phone number to a stable key so `+98 905 750 1288`,
     * `00989057501288` and `09057501288` all map to the same contact.
     */
    fun normalize(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        // Drop an Iranian country code prefix, then a trunk leading zero.
        if (digits.startsWith("98") && digits.length > 10) digits = digits.substring(2)
        digits = digits.trimStart('0')
        return digits.takeLast(10)
    }

    /** Loads a normalized-number -> display-name map. Requires READ_CONTACTS. */
    fun loadNames(context: Context): Map<String, String> {
        val names = HashMap<String, String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                    val number = cursor.getString(numberIdx) ?: continue
                    val key = normalize(number)
                    if (key.isNotEmpty()) names.putIfAbsent(key, name)
                }
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
        return names
    }

    /** The contact name for [address], or null when it is not a known contact. */
    fun displayName(address: String, names: Map<String, String>): String? =
        names[normalize(address)]?.takeIf { it.isNotBlank() }
}
