package com.smsspam.detector.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony

/** A single message shown inside a conversation thread. */
data class SmsMessage(
    val id: Long = 0L,
    val address: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
    val read: Boolean = false,
) {
    val isIncoming: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
}

/** One row in the conversation list: the latest message from an address. */
data class SmsConversation(
    val address: String,
    val snippet: String,
    val timestamp: Long,
    val messageCount: Int,
    val unreadCount: Int,
)

/**
 * Reads and updates the system SMS provider for the in-app conversations UI.
 * All calls require READ_SMS and degrade gracefully (empty / false) when the
 * permission is missing or the provider is unavailable.
 */
object SmsThreads {

    private val columns = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
    )

    /**
     * Builds the conversation list from every SMS, newest thread first. Rows are
     * aggregated in a single pass without allocating a [SmsMessage] per message,
     * which keeps large inboxes cheap to refresh.
     */
    fun readConversations(context: Context): List<SmsConversation> {
        val builders = HashMap<String, ConversationAggregate>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                columns,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: "Unknown"
                    val aggregate = builders.getOrPut(address) { ConversationAggregate() }
                    aggregate.count++
                    if (cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_INBOX &&
                        cursor.getInt(readIdx) != 1
                    ) {
                        aggregate.unread++
                    }
                    // Rows arrive newest-first, so the first row per address wins.
                    if (!aggregate.hasLatest) {
                        aggregate.hasLatest = true
                        aggregate.timestamp = cursor.getLong(dateIdx)
                        aggregate.snippet = cursor.getString(bodyIdx) ?: ""
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return builders.map { (address, aggregate) ->
            SmsConversation(
                address = address,
                snippet = aggregate.snippet,
                timestamp = aggregate.timestamp,
                messageCount = aggregate.count,
                unreadCount = aggregate.unread,
            )
        }.sortedByDescending { it.timestamp }
    }

    /** Mutable accumulator used while grouping inbox rows into threads. */
    private class ConversationAggregate {
        var count: Int = 0
        var unread: Int = 0
        var timestamp: Long = 0L
        var snippet: String = ""
        var hasLatest: Boolean = false
    }

    /** All messages exchanged with [address], oldest first. */
    fun readMessages(context: Context, address: String): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                columns,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(address),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (cursor.moveToNext()) {
                    result += SmsMessage(
                        id = cursor.getLong(idIdx),
                        address = cursor.getString(addressIdx) ?: address,
                        body = cursor.getString(bodyIdx) ?: "",
                        timestamp = cursor.getLong(dateIdx),
                        type = cursor.getInt(typeIdx),
                        read = cursor.getInt(readIdx) == 1,
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore; caller shows an empty thread.
        }
        return result
    }

    /** Marks every incoming message from [address] as read (best effort). */
    fun markThreadRead(context: Context, address: String): Boolean = try {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
            arrayOf(address),
        ) >= 0
    } catch (_: Exception) {
        false
    }
}
