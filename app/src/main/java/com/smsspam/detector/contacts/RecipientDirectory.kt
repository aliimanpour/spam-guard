package com.smsspam.detector.contacts

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

/**
 * A possible message recipient: a phone number (or alphanumeric sender id)
 * together with an optional contact name. Built from contacts, SMS threads and
 * the call log so the compose screen can suggest people the user has actually
 * been in touch with.
 */
data class Recipient(
    val number: String,
    val name: String? = null,
) {
    /** The contact name when known, otherwise the raw number. */
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

/**
 * Aggregates and searches recipient suggestions for the "To" field. Every
 * source is best effort: a missing permission (READ_CONTACTS, READ_SMS or
 * READ_CALL_LOG) simply drops that source instead of failing.
 */
object RecipientDirectory {

    /**
     * Builds the de-duplicated candidate list from saved contacts, existing SMS
     * threads and the call log. Numbers are matched by [ContactsResolver.normalize]
     * so `+98 905…`, `0098905…` and `0905…` collapse into one entry.
     */
    fun load(context: Context): List<Recipient> {
        val byKey = LinkedHashMap<String, Recipient>()

        fun add(raw: String?, name: String? = null) {
            val number = raw?.trim().orEmpty()
            if (number.isEmpty()) return
            val key = keyOf(number)
            if (key.isEmpty()) return
            val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
            val existing = byKey[key]
            when {
                existing == null -> byKey[key] = Recipient(number = number, name = cleanName)
                existing.name == null && cleanName != null ->
                    byKey[key] = existing.copy(name = cleanName)
            }
        }

        loadContacts(context) { add(it.first, it.second) }
        loadSmsAddresses(context) { add(it) }
        loadCallLog(context) { add(it.first, it.second) }

        return byKey.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Filters [all] by [query], matching the contact name, the raw number or the
     * significant digits of the number (so a Persian `09…` prefix finds `+98…`).
     */
    fun search(all: List<Recipient>, query: String, limit: Int = 20): List<Recipient> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val digits = ContactsResolver.normalize(text)
        return all.asSequence()
            .filter { recipient ->
                recipient.name?.contains(text, ignoreCase = true) == true ||
                    recipient.number.contains(text, ignoreCase = true) ||
                    (digits.isNotEmpty() &&
                        ContactsResolver.normalize(recipient.number).contains(digits))
            }
            .take(limit)
            .toList()
    }

    private fun keyOf(number: String): String {
        val normalized = ContactsResolver.normalize(number)
        return if (normalized.isNotEmpty()) normalized else number.lowercase()
    }

    private fun loadContacts(context: Context, add: (Pair<String?, String>) -> Unit) {
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
                    val number = cursor.getString(numberIdx) ?: continue
                    add(cursor.getString(nameIdx) to number)
                }
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
    }

    private fun loadSmsAddresses(context: Context, add: (String) -> Unit) {
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                null,
                null,
                null,
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                while (cursor.moveToNext()) add(cursor.getString(addressIdx) ?: continue)
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
    }

    private fun loadCallLog(context: Context, add: (Pair<String?, String>) -> Unit) {
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx) ?: continue
                    add(cursor.getString(nameIdx) to number)
                }
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
    }
}
