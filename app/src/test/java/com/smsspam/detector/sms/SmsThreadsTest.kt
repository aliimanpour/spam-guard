package com.smsspam.detector.sms

import android.provider.Telephony
import com.smsspam.detector.sms.SmsThreads.buildConversations
import com.smsspam.detector.sms.SmsThreads.mergePages
import com.smsspam.detector.sms.SmsThreads.parseRecipientIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsThreadsTest {

    private val addresses = mapOf(
        1L to "09120000000",
        2L to "ali@example.com",
    )

    private fun thread(
        threadId: Long,
        date: Long,
        recipients: String,
        snippet: String = "hi",
        count: Int = 1,
    ) = SmsThreadRow(
        threadId = threadId,
        date = date,
        messageCount = count,
        recipientIds = recipients,
        snippet = snippet,
    )

    private fun message(id: Long, timestamp: Long = id) = SmsMessage(
        id = id,
        address = "09120000000",
        body = "body $id",
        timestamp = timestamp,
    )

    @Test
    fun `parses space separated recipient ids`() {
        assertEquals(listOf(3L, 7L, 11L), parseRecipientIds("3 7 11"))
        assertEquals(listOf(3L), parseRecipientIds("3"))
    }

    @Test
    fun `blank or malformed recipient ids parse to nothing`() {
        assertTrue(parseRecipientIds(null).isEmpty())
        assertTrue(parseRecipientIds("").isEmpty())
        assertTrue(parseRecipientIds("  x  ").isEmpty())
    }

    @Test
    fun `builds conversations newest first with unread counts`() {
        val conversations = buildConversations(
            threads = listOf(
                thread(threadId = 10, date = 200L, recipients = "1", snippet = "newer"),
                thread(threadId = 11, date = 100L, recipients = "2", snippet = "older", count = 4),
            ),
            addresses = addresses,
            unreadByAddress = mapOf("09120000000" to 3),
        )

        assertEquals(listOf("09120000000", "ali@example.com"), conversations.map { it.address })
        assertEquals(listOf(10L, 11L), conversations.map { it.threadId })
        assertEquals(listOf("newer", "older"), conversations.map { it.snippet })
        assertEquals(3, conversations[0].unreadCount)
        assertEquals(0, conversations[1].unreadCount)
        assertEquals(4, conversations[1].messageCount)
    }

    @Test
    fun `skips threads without a single resolvable recipient`() {
        val conversations = buildConversations(
            threads = listOf(
                thread(threadId = 1, date = 300L, recipients = ""), // no recipients
                thread(threadId = 2, date = 250L, recipients = "4 5"), // MMS group thread
                thread(threadId = 3, date = 200L, recipients = "99"), // unknown id
                thread(threadId = 4, date = 100L, recipients = "1"),
            ),
            addresses = addresses,
            unreadByAddress = emptyMap(),
        )

        assertEquals(listOf("09120000000"), conversations.map { it.address })
        assertEquals(4L, conversations.single().threadId)
    }

    @Test
    fun `merges pages deduplicating by id and sorting oldest first`() {
        val newestPage = listOf(message(1), message(2), message(3))
        val olderPage = listOf(message(2), message(3), message(4)) // ids 2-3 overlap

        val merged = mergePages(newestPage, olderPage)

        assertEquals(listOf(1L, 2L, 3L, 4L), merged.map { it.id })
        assertTrue(merged.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
    }

    @Test
    fun `merge keeps empty pages harmless`() {
        assertEquals(listOf(1L), mergePages(emptyList(), listOf(message(1))).map { it.id })
        assertFalse(mergePages(emptyList(), emptyList()).isNotEmpty())
    }

    @Test
    fun `maps provider delivery status to a send status`() {
        val sentType = Telephony.Sms.MESSAGE_TYPE_SENT
        // No delivery report yet -> single tick.
        assertEquals(SendStatus.SENT, SmsMessage.statusFor(sentType, Telephony.Sms.STATUS_NONE))
        // Delivery report received -> two ticks.
        assertEquals(SendStatus.DELIVERED, SmsMessage.statusFor(sentType, Telephony.Sms.STATUS_COMPLETE))
        // Still queued at the radio -> clock.
        assertEquals(SendStatus.SENDING, SmsMessage.statusFor(Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.STATUS_NONE))
        assertEquals(SendStatus.SENDING, SmsMessage.statusFor(sentType, Telephony.Sms.STATUS_PENDING))
        // Failure wins over any delivery state.
        assertEquals(SendStatus.FAILED, SmsMessage.statusFor(sentType, Telephony.Sms.STATUS_FAILED))
        assertEquals(SendStatus.FAILED, SmsMessage.statusFor(Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_NONE))
    }
}