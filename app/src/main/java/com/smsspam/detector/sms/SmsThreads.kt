package com.smsspam.detector.sms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import androidx.core.content.ContextCompat

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
    /** System thread id; 0 when unknown. Lets thread reads use the indexed `thread_id` column. */
    val threadId: Long = 0L,
    val snippet: String,
    val timestamp: Long,
    val messageCount: Int,
    val unreadCount: Int,
)

/** A window of a thread's messages, ordered oldest first. */
data class SmsThreadPage(
    val messages: List<SmsMessage>,
    /** True when the provider has more messages older than this page. */
    val hasMore: Boolean,
)

/** One row of the system threads table: per-thread aggregates precomputed by the provider. */
data class SmsThreadRow(
    val threadId: Long,
    val date: Long,
    val messageCount: Int,
    val recipientIds: String,
    val snippet: String,
)

/**
 * Reads and updates the system SMS provider for the in-app conversations UI.
 * All calls require READ_SMS and degrade gracefully (empty / false) when the
 * permission is missing or the provider is unavailable.
 *
 * Nothing here scans every message body. The conversation list is built from
 * the provider's precomputed threads table (`content://mms-sms/conversations`
 * with `simple=true`) joined against the small canonical-addresses table, and
 * a thread's contents are read one page at a time by `thread_id`, which the
 * sms table indexes (`(thread_id, date)`). That keeps opening the list and any
 * thread O(page) instead of O(inbox).
 */
object SmsThreads {

    /** Messages fetched per page when a conversation is opened or scrolled. */
    const val PAGE_SIZE = 60

    private val messageColumns = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
    )

    /** True when READ_SMS is granted, i.e. the conversations UI has anything to read. */
    fun canRead(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Builds the conversation list in three cheap queries: the threads table
     * (one precomputed row per thread), the canonical-addresses lookup, and a
     * narrow unread pass that reads only the address column. The result is
     * newest first, ready for the UI.
     */
    fun readConversations(context: Context): List<SmsConversation> =
        buildConversations(
            threads = queryThreads(context),
            addresses = queryCanonicalAddresses(context),
            unreadByAddress = queryUnreadByAddress(context),
        )

    /** Pure: turns provider rows into the UI conversation list, newest first. */
    fun buildConversations(
        threads: List<SmsThreadRow>,
        addresses: Map<Long, String>,
        unreadByAddress: Map<String, Int>,
    ): List<SmsConversation> = threads.mapNotNull { row ->
        val ids = parseRecipientIds(row.recipientIds)
        // Multi-recipient rows are MMS group threads, which this SMS-only view
        // never matched before; unresolvable ids cannot be keyed by address.
        if (ids.size != 1) return@mapNotNull null
        val address = addresses[ids.single()] ?: return@mapNotNull null
        SmsConversation(
            address = address,
            threadId = row.threadId,
            snippet = row.snippet,
            timestamp = row.date,
            messageCount = row.messageCount,
            unreadCount = unreadByAddress[address] ?: 0,
        )
    }.sortedByDescending { it.timestamp }

    /** Pure: parses the threads table's space-separated recipient ids ("3 7 11"). */
    fun parseRecipientIds(raw: String?): List<Long> =
        raw?.split(' ')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    /** Pure: merges already-loaded pages, deduplicating by row id, oldest first. */
    fun mergePages(vararg pages: List<SmsMessage>): List<SmsMessage> =
        pages.toList().flatten().distinctBy { it.id }.sortedBy { it.timestamp }

    /**
     * Reads one window of a thread by id, newest first in the provider but
     * returned oldest-first for the UI. The `(thread_id, date)` index lets the
     * provider stop after [limit] + 1 rows, so page loads stay O(page); the
     * extra row only signals [SmsThreadPage.hasMore].
     */
    fun readThreadPage(
        context: Context,
        threadId: Long,
        before: Long? = null,
        limit: Int = PAGE_SIZE,
    ): SmsThreadPage {
        if (threadId <= 0L) return SmsThreadPage(emptyList(), hasMore = false)
        val selection = buildString {
            append("${Telephony.Sms.THREAD_ID} = ?")
            if (before != null) append(" AND ${Telephony.Sms.DATE} < ?")
        }
        val args = buildList {
            add(threadId.toString())
            if (before != null) add(before.toString())
        }.toTypedArray()
        val result = mutableListOf<SmsMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                messageColumns,
                selection,
                args,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                // Stop one row past the page: the surplus proves more exist.
                while (cursor.moveToNext() && result.size < limit + 1) {
                    result += SmsMessage(
                        id = cursor.getLong(idIdx),
                        address = cursor.getString(addressIdx) ?: "",
                        body = cursor.getString(bodyIdx) ?: "",
                        timestamp = cursor.getLong(dateIdx),
                        type = cursor.getInt(typeIdx),
                        read = cursor.getInt(readIdx) == 1,
                    )
                }
            }
        } catch (_: Exception) {
            return SmsThreadPage(emptyList(), hasMore = false)
        }
        val hasMore = result.size > limit
        if (hasMore) result.removeAt(result.size - 1)
        result.reverse()
        return SmsThreadPage(result, hasMore)
    }

    /**
     * Resolves the system thread id for an address, for opens that bypass the
     * conversation list (deep links, direct opens before the list has loaded).
     * The query stops at the first matching row, so it is cheap even though
     * `address` has no index.
     */
    fun resolveThreadId(context: Context, address: String): Long = try {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(address),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    } catch (_: Exception) {
        0L
    }

    /** Marks every incoming message of [threadId] as read (best effort). */
    fun markThreadRead(context: Context, threadId: Long): Boolean = try {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
            arrayOf(threadId.toString()),
        ) >= 0
    } catch (_: Exception) {
        false
    }

    /**
     * Threads table via the cheap `simple=true` path, which queries the
     * precomputed threads table directly instead of the expensive SMS+MMS
     * union query. The provider returns rows newest first.
     */
    private fun queryThreads(context: Context): List<SmsThreadRow> {
        val uri = Telephony.Threads.CONTENT_URI.buildUpon()
            .appendQueryParameter("simple", "true")
            .build()
        val result = mutableListOf<SmsThreadRow>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Threads._ID,
                    Telephony.Threads.DATE,
                    Telephony.Threads.MESSAGE_COUNT,
                    Telephony.Threads.RECIPIENT_IDS,
                    Telephony.Threads.SNIPPET,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Threads._ID)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Threads.DATE)
                val countIdx = cursor.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT)
                val recipientsIdx = cursor.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)
                val snippetIdx = cursor.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)
                while (cursor.moveToNext()) {
                    result += SmsThreadRow(
                        threadId = cursor.getLong(idIdx),
                        date = cursor.getLong(dateIdx),
                        messageCount = cursor.getInt(countIdx),
                        recipientIds = cursor.getString(recipientsIdx) ?: "",
                        snippet = cursor.getString(snippetIdx) ?: "",
                    )
                }
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
        return result
    }

    /**
     * Canonical address ids -> numbers. The threads table stores recipient ids,
     * not numbers; this small table (one row per distinct address) maps them.
     * AOSP keeps it under `mms-sms` and publishes no Uri constant, hence the
     * literal — stable across every Android release.
     */
    private fun queryCanonicalAddresses(context: Context): Map<Long, String> {
        val result = HashMap<Long, String>()
        try {
            context.contentResolver.query(
                Uri.parse("content://mms-sms/canonical-addresses"),
                arrayOf(BaseColumns._ID, Telephony.CanonicalAddressesColumns.ADDRESS),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.CanonicalAddressesColumns.ADDRESS)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    result[cursor.getLong(idIdx)] = address
                }
            }
        } catch (_: Exception) {
            // Empty map; buildConversations drops threads it cannot resolve.
        }
        return result
    }

    /** Unread counts per address from the inbox, reading only the address column. */
    private fun queryUnreadByAddress(context: Context): Map<String, Int> {
        val counts = HashMap<String, Int>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.READ} = 0",
                null,
                null,
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    counts[address] = (counts[address] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
            // Zero counts; badges degrade to read state.
        }
        return counts
    }
}