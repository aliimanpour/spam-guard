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
 * Everything the conversations UI needs from one pass over the SMS provider:
 * the thread summaries and the messages of every thread, keyed by address.
 */
data class InboxThreads(
    val conversations: List<SmsConversation>,
    val messagesByAddress: Map<String, List<SmsMessage>>,
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
     * Builds the conversation list and every thread's messages in a single
     * pass, oldest message first. Reading the inbox once and keeping the
     * messages in memory is what lets opening a thread render instantly instead
     * of waiting on a fresh provider query.
     */
    fun readInbox(context: Context): InboxThreads {
        val builders = HashMap<String, ConversationAggregate>()
        val messages = HashMap<String, MutableList<SmsMessage>>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                columns,
                null,
                null,
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: "Unknown"
                    val type = cursor.getInt(typeIdx)
                    val timestamp = cursor.getLong(dateIdx)
                    val body = cursor.getString(bodyIdx) ?: ""
                    val read = cursor.getInt(readIdx) == 1

                    val aggregate = builders.getOrPut(address) { ConversationAggregate() }
                    aggregate.count++
                    if (type == Telephony.Sms.MESSAGE_TYPE_INBOX && !read) {
                        aggregate.unread++
                    }
                    // Rows arrive oldest-first, so the last row per address is the
                    // most recent message and becomes the thread's snippet.
                    aggregate.timestamp = timestamp
                    aggregate.snippet = body

                    messages.getOrPut(address) { ArrayList() } += SmsMessage(
                        id = cursor.getLong(idIdx),
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        type = type,
                        read = read,
                    )
                }
            }
        } catch (_: Exception) {
            return InboxThreads(emptyList(), emptyMap())
        }

        val conversations = builders.map { (address, aggregate) ->
            SmsConversation(
                address = address,
                snippet = aggregate.snippet,
                timestamp = aggregate.timestamp,
                messageCount = aggregate.count,
                unreadCount = aggregate.unread,
            )
        }.sortedByDescending { it.timestamp }

        return InboxThreads(conversations = conversations, messagesByAddress = messages)
    }

    /** Mutable accumulator used while grouping inbox rows into threads. */
    private class ConversationAggregate {
        var count: Int = 0
        var unread: Int = 0
        var timestamp: Long = 0L
        var snippet: String = ""
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
