package com.smsspam.detector.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.contacts.ContactsResolver
import com.smsspam.detector.contacts.Recipient
import com.smsspam.detector.contacts.RecipientDirectory
import com.smsspam.detector.data.DataTransfer
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.model.ScheduledStatus
import com.smsspam.detector.sms.SmsAlarmScheduler
import com.smsspam.detector.sms.SmsConversation
import com.smsspam.detector.sms.SmsInbox
import com.smsspam.detector.sms.SmsMessage
import com.smsspam.detector.sms.SmsSender
import com.smsspam.detector.sms.SmsSims
import com.smsspam.detector.sms.SmsThreadPage
import com.smsspam.detector.sms.SmsThreads
import com.smsspam.detector.sms.SimSubscription
import com.smsspam.detector.sms.ThreadCache
import com.smsspam.detector.sms.restoreToInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off messages surfaced to the UI (rendered as snackbars). */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

/** Result of scanning the system inbox for spam. */
sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val scanned: Int, val caught: Int, val removed: Int) : ScanState
}

/** Progress of loading older messages for the open conversation. */
sealed interface OlderState {
    /** No thread open, or nothing known about older messages yet. */
    data object Idle : OlderState
    /** Older messages may exist and the next page is not loading. */
    data object Ready : OlderState
    /** A page of older messages is loading. */
    data object Loading : OlderState
    /** Every message of the thread is loaded. */
    data object EndReached : OlderState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SpamGuardApp).container

    /** Live, shared with the SMS receiver so the UI never goes stale. */
    val rules: StateFlow<List<Rule>> = container.ruleRepository.rules
    val messages: StateFlow<List<QuarantineMessage>> = container.messageRepository.messages
    val scheduled: StateFlow<List<ScheduledMessage>> = container.scheduleRepository.scheduled

    // --- Settings (auto unsubscribe) ----------------------------------------

    /** Replies to blocked subscription ads with their unsubscribe code, once per sender. */
    private val _autoUnsubscribe = MutableStateFlow(container.settingsStore.autoUnsubscribe)
    val autoUnsubscribe: StateFlow<Boolean> = _autoUnsubscribe.asStateFlow()

    /** Which code to send: [SettingsStore.CODE_AUTO], "11" or "12". */
    private val _unsubscribeCode = MutableStateFlow(container.settingsStore.unsubscribeCode)
    val unsubscribeCode: StateFlow<String> = _unsubscribeCode.asStateFlow()

    fun setAutoUnsubscribe(enabled: Boolean) {
        container.settingsStore.autoUnsubscribe = enabled
        _autoUnsubscribe.value = enabled
    }

    fun setUnsubscribeCode(code: String) {
        container.settingsStore.unsubscribeCode = code
        _unsubscribeCode.value = code
    }

    // --- SIM cards ----------------------------------------------------------

    private val _sims = MutableStateFlow<List<SimSubscription>>(emptyList())
    val sims: StateFlow<List<SimSubscription>> = _sims.asStateFlow()

    private var simsJob: Job? = null

    /**
     * Loads the active SIMs (needs READ_PHONE_STATE). Cached; pass [force]
     * after the permission is granted.
     */
    fun refreshSims(force: Boolean = false) {
        if (simsJob?.isActive == true) return
        if (!force && _sims.value.isNotEmpty()) return
        simsJob = viewModelScope.launch {
            _sims.value = withContext(Dispatchers.IO) { SmsSims.active(appContext) }
        }
    }

    /**
     * The subscription id remembered for [address], validated against the
     * active SIMs; 0 when the conversation should use the default SIM.
     */
    fun selectedSubId(address: String): Int {
        val wanted = container.simStore.subIdFor(address)
        return wanted.takeIf { _sims.value.any { sim -> sim.subId == it } } ?: 0
    }

    /** Remembers [subId] as the outgoing SIM for [address]. */
    fun setSim(address: String, subId: Int) {
        container.simStore.setSubId(address, subId)
    }

    private val _conversations = MutableStateFlow<List<SmsConversation>>(emptyList())
    val conversations: StateFlow<List<SmsConversation>> = _conversations.asStateFlow()

    private val _conversation = MutableStateFlow<List<SmsMessage>>(emptyList())
    val conversation: StateFlow<List<SmsMessage>> = _conversation.asStateFlow()

    /** True while the first page of a thread the cache has never seen is loading. */
    private val _threadLoading = MutableStateFlow(false)
    val threadLoading: StateFlow<Boolean> = _threadLoading.asStateFlow()

    private val _contacts = MutableStateFlow<Map<String, String>>(emptyMap())
    val contacts: StateFlow<Map<String, String>> = _contacts.asStateFlow()

    private val _recipients = MutableStateFlow<List<Recipient>>(emptyList())
    val recipients: StateFlow<List<Recipient>> = _recipients.asStateFlow()

    private val _isDefaultSms = MutableStateFlow(SmsInbox.isDefaultSmsApp(app))
    val isDefaultSms: StateFlow<Boolean> = _isDefaultSms.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    fun consumeEvent() { _events.value = null }

    // --- Rules -------------------------------------------------------------

    fun rule(id: String?): Rule? = rules.value.firstOrNull { it.id == id }

    fun upsertRule(rule: Rule) {
        val current = rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) current[index] = rule else current.add(rule)
        container.ruleRepository.save(current)
    }

    fun deleteRule(id: String) {
        container.ruleRepository.save(rules.value.filterNot { it.id == id })
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        container.ruleRepository.save(rules.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun resetRules() {
        container.ruleRepository.resetToDefaults()
        _events.value = UiEvent.Message("Preset rules restored")
    }

    // --- Messages ----------------------------------------------------------

    fun markNotSpam(message: QuarantineMessage) {
        // Give the message back to the system app when possible, then keep the record.
        val restored = restoreToInbox(getApplication(), message)
        container.messageRepository.updateStatus(
            message.id,
            if (restored) QuarantineStatus.RESTORED else QuarantineStatus.NOT_SPAM,
        )
        _events.value = UiEvent.Message(
            if (restored) "Restored to the Messages app" else "Marked as not spam"
        )
    }

    fun restore(message: QuarantineMessage) {
        val restored = restoreToInbox(getApplication(), message)
        container.messageRepository.updateStatus(message.id, QuarantineStatus.RESTORED)
        _events.value = UiEvent.Message(
            if (restored) "Restored to the Messages app" else "Marked as restored"
        )
    }

    fun deleteMessage(id: String) {
        container.messageRepository.delete(id)
    }

    fun clearAll() {
        container.messageRepository.clearAll()
    }

    // --- Blocked badge -----------------------------------------------------

    /** Persisted moment after which a blocked message counts as unread. */
    private val blockedSeenAt = MutableStateFlow(container.unreadStore.lastSeenAt())

    /**
     * Blocked messages the user has not seen yet: everything caught after the
     * last time the Blocked tab was opened or the app was exited. The moment
     * is one persisted timestamp ([UnreadStore]), so exiting the app resets
     * the badge to zero while messages received in the meantime still count.
     */
    val newBlockedCount: StateFlow<Int> =
        combine(messages, blockedSeenAt) { list, seenAt ->
            list.count { it.isBlocked && it.timestamp > seenAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Clears the Blocked-tab badge; called when the tab is opened/left or the app is exited. */
    fun markBlockedSeen() {
        container.unreadStore.markSeen()
        blockedSeenAt.value = container.unreadStore.lastSeenAt()
    }

    // --- Inbox scan --------------------------------------------------------

    fun scanInbox() {
        if (_scanState.value == ScanState.Scanning) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                val inbox = SmsInbox.readInbox(app)
                val rules = rules.value
                val caught = mutableListOf<Pair<com.smsspam.detector.sms.InboxSms, QuarantineMessage>>()
                inbox.forEach { sms ->
                    val match = RuleEngine.firstMatch(sms.sender, sms.body, rules) ?: return@forEach
                    caught += sms to SmsInbox.toQuarantine(sms, match.id, match.name)
                }
                container.messageRepository.addAll(caught.map { it.second })
                var removed = 0
                caught.forEach { (sms, _) ->
                    if (sms.id > 0 && SmsInbox.delete(app, sms)) removed++
                }
                ScanState.Done(scanned = inbox.size, caught = caught.size, removed = removed)
            }
            _scanState.value = result
        }
    }

    fun resetScanState() { _scanState.value = ScanState.Idle }

    // --- Conversations -----------------------------------------------------

    private var refreshJob: Job? = null
    private var threadJob: Job? = null
    private var olderJob: Job? = null
    private var contactsJob: Job? = null
    private var recipientsJob: Job? = null

    /**
     * Newest page of the most recently opened threads. Re-opening a thread
     * paints from here with zero provider work; entries are bounded (see
     * [ThreadCache]) so memory never grows with the inbox.
     */
    private val threadCache = ThreadCache()

    /** Address of the thread currently on screen, or null. */
    @Volatile
    private var openAddress: String? = null

    /** System thread id of the open conversation; 0 until resolved. */
    @Volatile
    private var openThreadId = 0L

    /** When the conversation list was last read, for the resume freshness window. */
    @Volatile
    private var summariesLoadedAt = 0L

    private val _olderState = MutableStateFlow<OlderState>(OlderState.Idle)
    val olderState: StateFlow<OlderState> = _olderState.asStateFlow()

    private val appContext: Context get() = getApplication()

    /**
     * Watches the SMS provider so data changes push into the cached UI state
     * instead of the UI polling on every resume. Runs for the ViewModel's
     * lifetime, which spans backgrounding, so the cache is already fresh when
     * the user comes back.
     */
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // Coalesce bursts (incoming SMS, our own inserts, mark-read, bulk
            // deletes during a scan) into a single refresh.
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(PROVIDER_REFRESH_DEBOUNCE_MS)
                refreshConversationsNow()
            }
        }
    }

    init {
        appContext.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            /* notifyForDescendants = */ true,
            smsObserver,
        )
    }

    override fun onCleared() {
        appContext.contentResolver.unregisterContentObserver(smsObserver)
        super.onCleared()
    }

    /**
     * Refreshes the conversation list from the system SMS provider. When the
     * cached list is still inside the freshness window this returns without
     * touching the provider, so navigating back to the list is free; the
     * [smsObserver] keeps the cache current in between. Pass [force] to skip
     * the freshness window (after granting a permission, for example).
     */
    fun refreshConversations(force: Boolean = false) {
        if (!force && SystemClock.elapsedRealtime() - summariesLoadedAt < SUMMARIES_FRESH_MS) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { refreshConversationsNow() }
    }

    private suspend fun refreshConversationsNow() {
        val app = appContext
        if (!SmsThreads.canRead(app)) {
            _conversations.value = emptyList()
            return
        }
        val conversations = withContext(Dispatchers.IO) { SmsThreads.readConversations(app) }
        summariesLoadedAt = SystemClock.elapsedRealtime()
        _conversations.value = conversations
        refreshOpenThread()
    }

    /**
     * Reloads the open thread's newest page after a provider change and merges
     * it into what is already on screen, so new messages appear without the
     * list jumping or losing pages the user has scrolled to.
     */
    private suspend fun refreshOpenThread() {
        val address = openAddress ?: return
        val threadId = openThreadId
        if (threadId <= 0L) return
        val page = withContext(Dispatchers.IO) { SmsThreads.readThreadPage(appContext, threadId) }
        if (openAddress != address) return
        val merged = SmsThreads.mergePages(page.messages, _conversation.value)
        _conversation.value = merged
        threadCache.put(address, merged, _olderState.value !is OlderState.EndReached)
    }

    /**
     * Reloads the contact name lookup (requires READ_CONTACTS). The result is
     * reused across screens; pass [force] to bypass the cache after the user
     * grants the permission or imports data.
     */
    fun refreshContacts(force: Boolean = false) {
        if (contactsJob?.isActive == true) return
        if (!force && _contacts.value.isNotEmpty()) return
        contactsJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _contacts.value = withContext(Dispatchers.IO) { ContactsResolver.loadNames(app) }
        }
    }

    /** Re-checks whether Spam Guard currently holds the default SMS role. */
    fun refreshDefaultSms() {
        _isDefaultSms.value = SmsInbox.isDefaultSmsApp(getApplication())
    }

    /** Contact name for an address, or null when it is not a saved contact. */
    fun contactName(address: String): String? = ContactsResolver.displayName(address, _contacts.value)

    /**
     * Loads the recipient suggestion list (contacts, SMS threads and the call
     * log) used by the "To" field. Best effort and cached; it retries on the
     * next call while the list is still empty, so it fills in once the user
     * grants READ_CONTACTS / READ_CALL_LOG.
     */
    fun refreshRecipients() {
        if (recipientsJob?.isActive == true) return
        if (_recipients.value.isNotEmpty()) return
        recipientsJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _recipients.value = withContext(Dispatchers.IO) { RecipientDirectory.load(app) }
        }
    }

    /**
     * Opens a thread. The newest cached page renders immediately (never the
     * previously opened thread), then a single indexed provider read refreshes
     * that page and marks the thread read. Only that page is loaded; older
     * history arrives through [loadOlderMessages] as the user scrolls.
     */
    fun openConversation(address: String) {
        val trimmed = address.trim()
        threadJob?.cancel()
        olderJob?.cancel()
        if (trimmed.isBlank()) {
            openAddress = null
            openThreadId = 0L
            _conversation.value = emptyList()
            _olderState.value = OlderState.Idle
            _threadLoading.value = false
            return
        }
        openAddress = trimmed
        openThreadId = _conversations.value.firstOrNull { it.address == trimmed }?.threadId ?: 0L
        // Instant: paint the cached page, or an empty list while the first
        // page loads for a thread the cache has not seen yet.
        val cached = threadCache.get(trimmed)
        _conversation.value = cached?.messages ?: emptyList()
        _olderState.value = if (cached?.hasMore != false) OlderState.Ready else OlderState.EndReached
        // Only a thread the cache has never seen needs a loading hint; a cached
        // page paints immediately and just refreshes below.
        _threadLoading.value = cached == null
        threadJob = viewModelScope.launch {
            val app = appContext
            val loaded = withContext(Dispatchers.IO) {
                val threadId = openThreadId.takeIf { it > 0L }
                    ?: SmsThreads.resolveThreadId(app, trimmed)
                if (openAddress == trimmed) openThreadId = threadId
                if (threadId > 0L) SmsThreads.markThreadRead(app, threadId)
                SmsThreads.readThreadPage(app, threadId)
            }
            if (openAddress != trimmed) return@launch
            // Merge, not replace: keep pages the user already scrolled to.
            val merged = SmsThreads.mergePages(loaded.messages, _conversation.value)
            _conversation.value = merged
            threadCache.put(trimmed, merged, loaded.hasMore)
            _olderState.value = if (loaded.hasMore) OlderState.Ready else OlderState.EndReached
            _threadLoading.value = false
        }
    }

    /**
     * Loads the next page of older messages for the open thread. Called by the
     * UI when the scroll reaches the oldest loaded message; repeated calls
     * while a load is running are ignored, which makes scroll-triggered
     * paging self-throttling.
     */
    fun loadOlderMessages() {
        val address = openAddress ?: return
        if (_olderState.value != OlderState.Ready) return
        if (olderJob?.isActive == true) return
        val current = _conversation.value
        if (current.isEmpty()) return
        olderJob = viewModelScope.launch {
            _olderState.value = OlderState.Loading
            val app = appContext
            val before = current.minOf { it.timestamp }
            val page = withContext(Dispatchers.IO) {
                val threadId = openThreadId.takeIf { it > 0L }
                    ?: SmsThreads.resolveThreadId(app, address)
                if (threadId > 0L) {
                    SmsThreads.readThreadPage(app, threadId, before = before)
                } else {
                    SmsThreadPage(emptyList(), hasMore = false)
                }
            }
            if (openAddress != address) {
                _olderState.value = OlderState.Idle
                return@launch
            }
            if (page.messages.isEmpty()) {
                _olderState.value = OlderState.EndReached
            } else {
                // Merge against the live list: a provider refresh may have
                // added newer messages while this page was loading.
                val merged = SmsThreads.mergePages(page.messages, _conversation.value)
                _conversation.value = merged
                threadCache.put(address, merged, page.hasMore)
                _olderState.value = if (page.hasMore) OlderState.Ready else OlderState.EndReached
            }
        }
    }

    // --- Conversation prefetch ---------------------------------------------

    /** Conversations queued for a newest-page prefetch; most recent request last. */
    private val prefetchPending = ArrayDeque<SmsConversation>()

    private val prefetchLock = Any()
    private var prefetchWorker: Job? = null

    /**
     * Queues the newest page of [conversation] for prefetch into the thread
     * cache. The conversation list calls this for every row that becomes
     * visible while the user scrolls, so tapping a conversation paints
     * instantly instead of waiting on a provider read. Cached and queued
     * threads are skipped, and one worker drains the queue most-recent-first,
     * one O(page) indexed query at a time.
     */
    fun prefetchConversation(conversation: SmsConversation) {
        val address = conversation.address.trim()
        if (address.isBlank() || !SmsThreads.canRead(appContext)) return
        synchronized(prefetchLock) {
            if (threadCache.contains(address)) return
            if (prefetchPending.any { it.address == address }) return
            prefetchPending.addLast(conversation)
        }
        if (prefetchWorker?.isActive == true) return
        prefetchWorker = viewModelScope.launch {
            while (true) {
                val next = synchronized(prefetchLock) {
                    // Most-recent-first: the row the user just scrolled to is
                    // the one they are most likely to tap next.
                    prefetchPending.removeLastOrNull()
                } ?: return@launch
                val page = withContext(Dispatchers.IO) {
                    val threadId = next.threadId.takeIf { it > 0L }
                        ?: SmsThreads.resolveThreadId(appContext, next.address)
                    if (threadId > 0L) SmsThreads.readThreadPage(appContext, threadId)
                    else SmsThreadPage(emptyList(), hasMore = false)
                }
                if (page.messages.isNotEmpty()) {
                    threadCache.put(next.address, page.messages, page.hasMore)
                }
                delay(PREFETCH_STAGGER_MS)
            }
        }
    }

    /** Sends a message now (with the given SIM when provided) and refreshes the open thread. */
    fun sendSms(recipient: String, body: String, subId: Int = 0) {
        if (recipient.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) { SmsSender.send(app, recipient, body, subId) }
            if (ok) {
                _events.value = UiEvent.Message("Message sent")
                openConversation(SmsSender.parseRecipients(recipient).firstOrNull().orEmpty())
            } else {
                _events.value = UiEvent.Message("Unable to send message")
            }
        }
    }

    // --- Scheduled messages ------------------------------------------------

    fun scheduleMessage(recipient: String, body: String, sendAt: Long, subId: Int = 0): Boolean {
        if (recipient.isBlank() || body.isBlank()) return false
        val message = ScheduledMessage(recipient = recipient.trim(), body = body, sendAt = sendAt, subId = subId)
        container.scheduleRepository.add(message)
        SmsAlarmScheduler.schedule(getApplication(), message)
        _events.value = UiEvent.Message("Message scheduled")
        return true
    }

    fun cancelScheduled(id: String) {
        SmsAlarmScheduler.cancel(getApplication(), id)
        container.scheduleRepository.updateStatus(id, ScheduledStatus.CANCELLED)
    }

    fun deleteScheduled(id: String) {
        SmsAlarmScheduler.cancel(getApplication(), id)
        container.scheduleRepository.delete(id)
    }

    fun sendScheduledNow(id: String) {
        val message = scheduled.value.firstOrNull { it.id == id } ?: return
        SmsAlarmScheduler.cancel(getApplication(), id)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) { SmsSender.send(app, message.recipient, message.body) }
            container.scheduleRepository.updateStatus(
                id,
                if (ok) ScheduledStatus.SENT else ScheduledStatus.FAILED,
            )
            _events.value = UiEvent.Message(if (ok) "Message sent" else "Unable to send message")
        }
    }

    // --- Backup ------------------------------------------------------------

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = runCatching { withContext(Dispatchers.IO) { DataTransfer.export(app, uri, container) } }
            _events.value = UiEvent.Message(result.getOrElse { "Export failed: ${it.message}" })
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = runCatching { withContext(Dispatchers.IO) { DataTransfer.import(app, uri, container) } }
            result.onSuccess { refreshConversations(force = true) }
            _events.value = UiEvent.Message(result.getOrElse { "Import failed: ${it.message}" })
        }
    }

    private companion object {
        /** How long the cached conversation list is trusted before a resume re-queries. */
        const val SUMMARIES_FRESH_MS = 15_000L

        /** Debounce for provider change notifications from [smsObserver]. */
        const val PROVIDER_REFRESH_DEBOUNCE_MS = 300L

        /** Pause between prefetch page reads so scrolling never starves the provider. */
        const val PREFETCH_STAGGER_MS = 50L
    }
}
