package com.smsspam.detector.ui

import android.app.Application
import android.net.Uri
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
import com.smsspam.detector.sms.SmsThreads
import com.smsspam.detector.sms.restoreToInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SpamGuardApp).container

    /** Live, shared with the SMS receiver so the UI never goes stale. */
    val rules: StateFlow<List<Rule>> = container.ruleRepository.rules
    val messages: StateFlow<List<QuarantineMessage>> = container.messageRepository.messages
    val scheduled: StateFlow<List<ScheduledMessage>> = container.scheduleRepository.scheduled

    private val _conversations = MutableStateFlow<List<SmsConversation>>(emptyList())
    val conversations: StateFlow<List<SmsConversation>> = _conversations.asStateFlow()

    private val _conversation = MutableStateFlow<List<SmsMessage>>(emptyList())
    val conversation: StateFlow<List<SmsMessage>> = _conversation.asStateFlow()

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

    private var conversationsJob: Job? = null
    private var contactsJob: Job? = null
    private var conversationJob: Job? = null
    private var recipientsJob: Job? = null

    /**
     * Messages of every thread from the last inbox scan, keyed by address. This
     * is what makes opening a conversation instant: the thread renders from here
     * immediately while a background refresh picks up anything new.
     */
    @Volatile
    private var threadCache: Map<String, List<SmsMessage>> = emptyMap()

    /** Address of the thread currently on screen, or null. */
    @Volatile
    private var openAddress: String? = null

    /**
     * Reloads the conversation list from the system SMS provider in a single
     * pass that also caches every thread's messages. Concurrent requests are
     * coalesced so navigating between screens never launches overlapping inbox
     * reads.
     */
    fun refreshConversations() {
        if (conversationsJob?.isActive == true) return
        conversationsJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val inbox = withContext(Dispatchers.IO) { SmsThreads.readInbox(app) }
            _conversations.value = inbox.conversations
            threadCache = inbox.messagesByAddress
            // Keep an already-open thread fresh without a visible reload.
            openAddress?.let { address ->
                inbox.messagesByAddress[address]?.let { _conversation.value = it }
            }
        }
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
     * Opens a thread. The messages render instantly from the cached inbox scan
     * (never the previously opened thread), while a background read refreshes
     * that one thread and marks it read.
     */
    fun openConversation(address: String) {
        conversationJob?.cancel()
        openAddress = address.ifBlank { null }
        if (address.isBlank()) {
            _conversation.value = emptyList()
            return
        }
        // Instant: serve the already-scanned thread, or an empty list when the
        // cache does not know it yet.
        _conversation.value = threadCache[address] ?: emptyList()
        conversationJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) {
                SmsThreads.markThreadRead(app, address)
                SmsThreads.readMessages(app, address)
            }
            if (openAddress == address) {
                threadCache = threadCache + (address to loaded)
                _conversation.value = loaded
            }
        }
    }

    /** Sends a message now and refreshes the open thread when it succeeds. */
    fun sendSms(recipient: String, body: String) {
        if (recipient.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) { SmsSender.send(app, recipient, body) }
            if (ok) {
                _events.value = UiEvent.Message("Message sent")
                openConversation(SmsSender.parseRecipients(recipient).firstOrNull().orEmpty())
            } else {
                _events.value = UiEvent.Message("Unable to send message")
            }
        }
    }

    // --- Scheduled messages ------------------------------------------------

    fun scheduleMessage(recipient: String, body: String, sendAt: Long): Boolean {
        if (recipient.isBlank() || body.isBlank()) return false
        val message = ScheduledMessage(recipient = recipient.trim(), body = body, sendAt = sendAt)
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
            result.onSuccess { refreshConversations() }
            _events.value = UiEvent.Message(result.getOrElse { "Import failed: ${it.message}" })
        }
    }
}
