package com.smsspam.detector.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadCacheTest {

    private fun message(id: Long, timestamp: Long = id) = SmsMessage(
        id = id,
        address = "09120000000",
        body = "body $id",
        timestamp = timestamp,
    )

    private fun cache(vararg addresses: String, maxThreads: Int = 12) = ThreadCache(maxThreads).apply {
        addresses.forEach { put(it, listOf(message(it.hashCode().toLong())), hasMore = false) }
    }

    @Test
    fun `returns the entry it stored`() {
        val cache = ThreadCache()
        cache.put("ali", listOf(message(1), message(2)), hasMore = true)

        val entry = cache.get("ali")
        assertNotNull(entry)
        assertEquals(listOf(1L, 2L), entry!!.messages.map { it.id })
        assertTrue(entry.hasMore)
    }

    @Test
    fun `missing address returns null`() {
        assertNull(ThreadCache().get("nobody"))
    }

    @Test
    fun `evicts the least recently used thread beyond capacity`() {
        val cache = cache("a", "b", "c", maxThreads = 2)
        // "a" was evicted when "c" arrived.
        assertNull(cache.get("a"))
        assertNotNull(cache.get("b"))
        assertNotNull(cache.get("c"))
        // "b" and "c" were both just read; "c" is most recent, so the next
        // insert evicts "b".
        cache.put("d", listOf(message(4)), hasMore = false)
        assertNull(cache.get("b"))
        assertNotNull(cache.get("c"))
        assertNotNull(cache.get("d"))
    }

    @Test
    fun `reading bumps recency`() {
        val cache = cache("a", "b", maxThreads = 2)
        cache.get("a") // refresh "a"; "b" is now the least recently used
        cache.put("c", listOf(message(3)), hasMore = false)
        assertNull(cache.get("b"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("c"))
    }

    @Test
    fun `keeps only the newest messages per thread`() {
        val cache = ThreadCache(maxMessagesPerThread = 3)
        val all = (1L..5L).map { message(it, timestamp = it) }
        cache.put("ali", all, hasMore = false)

        val entry = cache.get("ali")!!
        assertEquals(listOf(3L, 4L, 5L), entry.messages.map { it.id })
        // Dropping older messages means older ones still exist in the provider.
        assertTrue(entry.hasMore)
    }

    @Test
    fun `hasMore stays true once reported`() {
        val cache = ThreadCache(maxMessagesPerThread = 10)
        cache.put("ali", (1L..3L).map { message(it) }, hasMore = true)
        assertTrue(cache.get("ali")!!.hasMore)

        cache.put("ali", (4L..6L).map { message(it) }, hasMore = false)
        assertFalse(cache.get("ali")!!.hasMore)
    }

    @Test
    fun `clear empties every entry`() {
        val cache = cache("a", "b")
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}