package com.smsspam.detector.sms

/**
 * Bounded, thread-safe in-memory cache of recently opened conversations, so
 * re-opening a thread paints instantly from its newest page instead of waiting
 * on a provider query. Least-recently-*used* threads are evicted first, and
 * each entry keeps at most the newest [maxMessagesPerThread] messages.
 */
class ThreadCache(
    private val maxThreads: Int = DEFAULT_MAX_THREADS,
    private val maxMessagesPerThread: Int = DEFAULT_MAX_MESSAGES,
) {

    /** The cached window of one thread, oldest first. */
    data class Entry(
        val messages: List<SmsMessage>,
        /** True when older messages exist beyond this window. */
        val hasMore: Boolean,
    )

    // accessOrder=true turns every get/put into a recency bump, feeding LRU.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxThreads
    }

    /** Cached window for [address], bumping it to most-recently-used. */
    @Synchronized
    fun get(address: String): Entry? = entries[address]

    /** True when [address] has a cached window, without bumping its recency. */
    @Synchronized
    fun contains(address: String): Boolean = entries.containsKey(address)

    /**
     * Caches [messages] (oldest first) for [address], keeping only the newest
     * window. [hasMore] is sticky when trimming has to drop older pages.
     */
    @Synchronized
    fun put(address: String, messages: List<SmsMessage>, hasMore: Boolean) {
        val overflow = messages.size > maxMessagesPerThread
        entries[address] = Entry(
            messages = if (overflow) messages.takeLast(maxMessagesPerThread) else messages,
            hasMore = hasMore || overflow,
        )
    }

    @Synchronized
    fun clear() = entries.clear()

    /** Removes the cached window for [address] (e.g. after the thread is deleted). */
    @Synchronized
    fun evict(address: String) { entries.remove(address) }

    companion object {
        // Large enough that scrolling a long list — where every visible row is
        // prefetched — still leaves recently opened threads cached.
        const val DEFAULT_MAX_THREADS = 24
        const val DEFAULT_MAX_MESSAGES = 200
    }
}