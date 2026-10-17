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
import com.smsspam.detector.engine.AutomationEngine
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.notify.Notifier
import com.smsspam.detector.model.AutoReply
import com.smsspam.detector.model.ContactEntry
import com.smsspam.detector.model.ContactGroup
import com.smsspam.detector.model.ConversationSettings
import com.smsspam.detector.model.PendingFollowUp
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.SavedMessage
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.model.ScheduledStatus
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTrigger
import com.smsspam.detector.sms.AutomationRunner
import com.smsspam.detector.sms.ConversationPdf
import com.smsspam.detector.sms.SmsAlarmScheduler
import com.smsspam.detector.sms.WorkflowScheduler
import com.smsspam.detector.sms.SmsConversation
import com.smsspam.detector.sms.SmsInbox
import com.smsspam.detector.sms.SmsMessage
import com.smsspam.detector.sms.SmsSender
import com.smsspam.detector.sms.SmsSims
import com.smsspam.detector.sms.SmsThreadPage
import com.smsspam.detector.sms.SmsThreads
import com.smsspam.detector.sms.SendQueue
import com.smsspam.detector.sms.SendStatus
import com.smsspam.detector.sms.SimSubscription
import com.smsspam.detector.sms.ThreadCache
import com.smsspam.detector.sms.restoreToInbox
import com.smsspam.detector.ui.theme.ThemeMode
import com.smsspam.detector.ui.theme.ThemePalette
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

/** How long a delete can be undone before it is committed. */
private const val DELETE_UNDO_MILLIS = 7_000L

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SpamGuardApp).container

    /** Live, shared with the SMS receiver so the UI never goes stale. */
    val rules: StateFlow<List<Rule>> = container.ruleRepository.rules

    /** Master switch for the built-in preset pack shown as "Spam Protection". */
    val protectionEnabled: StateFlow<Boolean> = container.ruleRepository.protectionEnabled
    val messages: StateFlow<List<QuarantineMessage>> = container.messageRepository.messages
    val scheduled: StateFlow<List<ScheduledMessage>> = container.scheduleRepository.scheduled

    // --- Settings (auto unsubscribe) ----------------------------------------

    /** Replies to blocked subscription ads with their unsubscribe code, once per sender. */
    private val _autoUnsubscribe = MutableStateFlow(container.settingsStore.autoUnsubscribe)
    val autoUnsubscribe: StateFlow<Boolean> = _autoUnsubscribe.asStateFlow()

    /** Which code to send: [SettingsStore.CODE_AUTO], "11" or "12". */
    private val _unsubscribeCode = MutableStateFlow(container.settingsStore.unsubscribeCode)
    val unsubscribeCode: StateFlow<String> = _unsubscribeCode.asStateFlow()

    /** When true, outgoing messages request delivery reports (two ticks when delivered). */
    private val _deliveryReports = MutableStateFlow(container.settingsStore.deliveryReports)
    val deliveryReports: StateFlow<Boolean> = _deliveryReports.asStateFlow()

    fun setAutoUnsubscribe(enabled: Boolean) {
        container.settingsStore.autoUnsubscribe = enabled
        _autoUnsubscribe.value = enabled
    }

    fun setUnsubscribeCode(code: String) {
        container.settingsStore.unsubscribeCode = code
        _unsubscribeCode.value = code
    }

    fun setDeliveryReports(enabled: Boolean) {
        container.settingsStore.deliveryReports = enabled
        _deliveryReports.value = enabled
    }

    // --- Sending & replies --------------------------------------------------

    /**
     * When true (default), a message that fails to send with no signal is
     * queued and retried automatically with a growing backoff instead of being
     * dropped.
     */
    private val _autoRetry = MutableStateFlow(container.settingsStore.autoRetry)
    val autoRetry: StateFlow<Boolean> = _autoRetry.asStateFlow()

    fun setAutoRetry(enabled: Boolean) {
        container.settingsStore.autoRetry = enabled
        _autoRetry.value = enabled
    }

    /** When true (default), on-device reply suggestions are offered in a thread. */
    private val _smartReplies = MutableStateFlow(container.settingsStore.smartReplies)
    val smartReplies: StateFlow<Boolean> = _smartReplies.asStateFlow()

    fun setSmartReplies(enabled: Boolean) {
        container.settingsStore.smartReplies = enabled
        _smartReplies.value = enabled
    }

    // --- Appearance (theme) -------------------------------------------------

    /** Light/dark/system theme selection. */
    private val _themeMode = MutableStateFlow(ThemeMode.fromId(container.settingsStore.themeMode))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Accent color palette (or system dynamic color). */
    private val _palette = MutableStateFlow(ThemePalette.fromId(container.settingsStore.palette))
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    /** True when dark themes paint a true-black background for AMOLED screens. */
    private val _amoledDark = MutableStateFlow(container.settingsStore.amoledDark)
    val amoledDark: StateFlow<Boolean> = _amoledDark.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        container.settingsStore.themeMode = mode.id
        _themeMode.value = mode
    }

    fun setPalette(palette: ThemePalette) {
        container.settingsStore.palette = palette.id
        _palette.value = palette
    }

    fun setAmoledDark(enabled: Boolean) {
        container.settingsStore.amoledDark = enabled
        _amoledDark.value = enabled
    }

    // --- Saved messages -----------------------------------------------------

    /** Notes the user saved to themselves, oldest first. */
    val savedMessages: StateFlow<List<SavedMessage>> = container.savedMessagesRepository.messages

    /** Saves [body] as a note to self. Returns false for a blank body. */
    fun saveMessage(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        container.savedMessagesRepository.add(SavedMessage(body = trimmed))
        _events.value = UiEvent.Message("Saved")
        return true
    }

    /**
     * Saves one or more message bodies to Saved messages in a single action —
     * used by the per-message "Save" item and the multi-select Save button.
     */
    fun saveMessages(bodies: List<String>) {
        val notes = bodies.map { it.trim() }.filter { it.isNotEmpty() }
        if (notes.isEmpty()) return
        notes.forEach { container.savedMessagesRepository.add(SavedMessage(body = it)) }
        _events.value = UiEvent.Message(
            if (notes.size == 1) "Saved to Saved messages" else "Saved ${notes.size} messages"
        )
    }

    /** Deletes several saved messages in one write. */
    fun deleteSavedMessages(ids: Set<String>) {
        if (ids.isEmpty()) return
        container.savedMessagesRepository.deleteAll(ids)
        _events.value = UiEvent.Message(
            if (ids.size == 1) "Message deleted" else "Deleted ${ids.size} messages"
        )
    }

    // --- Auto-replies -------------------------------------------------------

    val autoReplies: StateFlow<List<AutoReply>> = container.autoReplyRepository.replies

    fun saveAutoReply(reply: AutoReply) {
        container.autoReplyRepository.save(reply)
        _events.value = UiEvent.Message("Auto-reply saved")
    }

    fun deleteAutoReply(id: String) {
        container.autoReplyRepository.delete(id)
    }

    fun setAutoReplyEnabled(id: String, enabled: Boolean) {
        container.autoReplyRepository.setEnabled(id, enabled)
    }

    // --- Workflows ----------------------------------------------------------

    val workflows: StateFlow<List<Workflow>> = container.workflowRepository.workflows
    val followUps: StateFlow<List<PendingFollowUp>> = container.followUpRepository.pending

    /** Saves a workflow and keeps its reminder/follow-up alarms in sync. */
    fun saveWorkflow(workflow: Workflow) {
        val previous = workflows.value.firstOrNull { it.id == workflow.id }
        container.workflowRepository.save(workflow)
        val app = getApplication<Application>()
        WorkflowScheduler.cancelReminder(app, workflow.id)
        if (workflow.enabled && workflow.trigger == WorkflowTrigger.REMINDER &&
            workflow.triggerAt > System.currentTimeMillis()
        ) {
            WorkflowScheduler.scheduleReminder(app, workflow)
        }
        // A workflow that is no longer a follow-up drops its queued follow-ups.
        if (previous?.trigger == WorkflowTrigger.FOLLOW_UP && workflow.trigger != WorkflowTrigger.FOLLOW_UP) {
            container.followUpRepository.removeForWorkflow(workflow.id).forEach {
                WorkflowScheduler.cancelFollowUp(app, it.id)
            }
        }
        _events.value = UiEvent.Message("Workflow saved")
    }

    fun deleteWorkflow(id: String) {
        val app = getApplication<Application>()
        WorkflowScheduler.cancelReminder(app, id)
        container.followUpRepository.removeForWorkflow(id).forEach {
            WorkflowScheduler.cancelFollowUp(app, it.id)
        }
        container.workflowRepository.delete(id)
    }

    fun setWorkflowEnabled(id: String, enabled: Boolean) {
        val workflow = workflows.value.firstOrNull { it.id == id } ?: return
        val updated = workflow.copy(enabled = enabled)
        container.workflowRepository.setEnabled(id, enabled)
        val app = getApplication<Application>()
        WorkflowScheduler.cancelReminder(app, id)
        if (enabled && updated.trigger == WorkflowTrigger.REMINDER &&
            updated.triggerAt > System.currentTimeMillis()
        ) {
            WorkflowScheduler.scheduleReminder(app, updated)
        }
    }

    /** Recipients a reminder workflow would message (for the editor preview). */
    fun recipientsForWorkflow(workflow: Workflow): List<String> =
        AutomationRunner.recipientsFor(container, workflow)

    // --- Contacts, tags & groups -------------------------------------------

    val contactBook: StateFlow<List<ContactEntry>> = container.contactsRepository.contacts
    val groups: StateFlow<List<ContactGroup>> = container.contactsRepository.groups

    fun allTags(): List<String> = container.contactsRepository.allTags()

    /** Numbers in a saved group (for the bulk-send recipient preview). */
    fun numbersInGroup(groupId: String): List<String> = container.contactsRepository.numbersInGroup(groupId)

    /** Numbers carrying a tag (for the bulk-send recipient preview). */
    fun numbersWithTag(tag: String): List<String> = container.contactsRepository.numbersWithTag(tag)

    fun saveContact(contact: ContactEntry) {
        container.contactsRepository.saveContact(contact)
    }

    fun deleteContact(id: String) {
        container.contactsRepository.deleteContact(id)
    }

    fun saveGroup(group: ContactGroup) {
        container.contactsRepository.saveGroup(group)
        _events.value = UiEvent.Message("Group saved")
    }

    fun deleteGroup(id: String) {
        container.contactsRepository.deleteGroup(id)
    }

    // --- Bulk sending -------------------------------------------------------

    /** Sends [body] to up to [MAX_BULK_CONTACTS] recipients, one SMS each. */
    fun sendBulk(recipients: List<String>, body: String, subId: Int = 0) {
        val targets = bulkTargets(recipients)
        if (targets.isEmpty() || body.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) { SmsSender.send(app, targets, body, subId) }
            _events.value = UiEvent.Message(
                if (ok) "Sent to ${targets.size} contacts" else "Unable to send bulk message"
            )
            if (ok) refreshConversations(force = true)
        }
    }

    /** Queues [body] for every recipient at [sendAt]. */
    fun scheduleBulk(recipients: List<String>, body: String, sendAt: Long, subId: Int = 0) {
        val targets = bulkTargets(recipients)
        if (targets.isEmpty() || body.isBlank()) return
        val app = getApplication<Application>()
        targets.forEach { recipient ->
            val message = ScheduledMessage(
                recipient = recipient,
                body = body,
                sendAt = sendAt,
                subId = subId,
            )
            container.scheduleRepository.add(message)
            SmsAlarmScheduler.schedule(app, message)
        }
        _events.value = UiEvent.Message("Scheduled for ${targets.size} contacts")
    }

    /** Deduplicated recipients, capped at the bulk limit. */
    private fun bulkTargets(recipients: List<String>): List<String> =
        AutomationEngine.dedupeNumbers(recipients).take(MAX_BULK_CONTACTS)

    // --- Per-conversation settings -----------------------------------------

    val conversationSettings: StateFlow<Map<String, ConversationSettings>> =
        container.conversationSettings.settings

    fun setConversationNotify(address: String, enabled: Boolean) =
        container.conversationSettings.setNotify(address, enabled)

    fun setConversationSound(address: String, enabled: Boolean) =
        container.conversationSettings.setSound(address, enabled)

    fun setConversationPinned(address: String, pinned: Boolean) =
        container.conversationSettings.setPinned(address, pinned)

    fun setConversationArchived(address: String, archived: Boolean) =
        container.conversationSettings.setArchived(address, archived)

    fun setConversationLocked(address: String, locked: Boolean) =
        container.conversationSettings.setLocked(address, locked)

    /** The saved draft for [address], or blank. */
    fun draft(address: String): String = container.conversationSettings.draft(address)

    fun setDraft(address: String, text: String) {
        if (address.isBlank()) return
        container.conversationSettings.setDraft(address, text)
    }

    fun clearDraft(address: String) {
        if (address.isBlank()) return
        container.conversationSettings.clearDraft(address)
    }

    // --- App lock -----------------------------------------------------------

    private val _appLockEnabled = MutableStateFlow(container.appLockStore.enabled)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appLockDeviceCredential = MutableStateFlow(container.appLockStore.deviceCredential)
    val appLockDeviceCredential: StateFlow<Boolean> = _appLockDeviceCredential.asStateFlow()

    /** False while the app is locked (session only; resets on process start). */
    private val _appUnlocked = MutableStateFlow(false)
    val appUnlocked: StateFlow<Boolean> = _appUnlocked.asStateFlow()

    /** Conversations unlocked for this session (per-conversation lock). */
    private val _unlockedConversations = MutableStateFlow<Set<String>>(emptySet())
    val unlockedConversations: StateFlow<Set<String>> = _unlockedConversations.asStateFlow()

    fun appLockHasPin(): Boolean = container.appLockStore.hasPin

    fun setAppLockEnabled(enabled: Boolean) {
        container.appLockStore.enabled = enabled
        _appLockEnabled.value = enabled
        // Don't lock the current session the moment it is switched on; the lock
        // takes effect on the next launch or when the app goes to background.
        _appUnlocked.value = true
    }

    fun setAppLockDeviceCredential(enabled: Boolean) {
        container.appLockStore.deviceCredential = enabled
        _appLockDeviceCredential.value = enabled
    }

    fun setAppPin(pin: String) = container.appLockStore.setPin(pin)

    fun clearAppPin() = container.appLockStore.clearPin()

    fun verifyAppPin(pin: String): Boolean = container.appLockStore.verifyPin(pin)

    fun unlockApp() { _appUnlocked.value = true }

    /** Re-locks the app (called when it goes to the background). */
    fun lockApp() {
        if (container.appLockStore.enabled) _appUnlocked.value = false
    }

    fun unlockConversation(address: String) {
        _unlockedConversations.value = _unlockedConversations.value + key(address)
    }

    fun isConversationUnlocked(address: String): Boolean = key(address) in _unlockedConversations.value

    private fun key(address: String): String =
        ContactsResolver.normalize(address).ifBlank { address.trim() }

    // --- Unknown-number notifications --------------------------------------

    private val _hideUnknownNotifications = MutableStateFlow(container.settingsStore.hideUnknownNotifications)
    val hideUnknownNotifications: StateFlow<Boolean> = _hideUnknownNotifications.asStateFlow()

    fun setHideUnknownNotifications(enabled: Boolean) {
        container.settingsStore.hideUnknownNotifications = enabled
        _hideUnknownNotifications.value = enabled
    }

    // --- Export -------------------------------------------------------------

    /** Writes the open conversation to a PDF at [uri]. */
    fun exportConversationPdf(uri: Uri, address: String, title: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val messages = _conversation.value
            val result = runCatching {
                withContext(Dispatchers.IO) { ConversationPdf.export(app, uri, title, messages) }
            }
            result.onSuccess { count -> Notifier.notifyExport(app, uri, title, count) }
            _events.value = UiEvent.Message(
                result.fold(
                    onSuccess = { "Exported $it messages to PDF" },
                    onFailure = { "PDF export failed: ${it.message}" },
                )
            )
        }
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

    /**
     * In-flight outgoing messages shown with a sending/sent/failed status icon
     * until the provider refresh confirms them. Uses negative ids so they never
     * collide with provider row ids.
     */
    private val _pendingSends = MutableStateFlow<List<SmsMessage>>(emptyList())
    private val pendingIdCounter = java.util.concurrent.atomic.AtomicLong(0L)

    /** The open thread's messages, merged with any in-flight sends, oldest first. */
    val conversation: StateFlow<List<SmsMessage>> =
        combine(_conversation, _pendingSends) { provider, pending ->
            if (pending.isEmpty()) {
                provider
            } else {
                // Drop a pending stub the moment its real provider row shows up,
                // so a just-sent message never appears twice.
                val outstanding = pending.filterNot { stub ->
                    provider.any { row ->
                        row.id > 0L &&
                            row.address == stub.address &&
                            row.body == stub.body &&
                            row.timestamp >= stub.timestamp - 60_000L
                    }
                }
                (provider + outstanding).distinctBy { it.id }.sortedBy { it.timestamp }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun setProtectionEnabled(enabled: Boolean) {
        container.ruleRepository.setProtectionEnabled(enabled)
        _events.value = UiEvent.Message(
            if (enabled) "Spam Protection enabled" else "Spam Protection disabled"
        )
    }

    /** Restores the built-in preset pack without touching custom rules. */
    fun resetProtection() {
        container.ruleRepository.resetPresetsKeepingCustom()
        _events.value = UiEvent.Message("Spam Protection reset")
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

    /** Bulk variant of [markNotSpam] used by multi-select. */
    fun markNotSpam(messages: List<QuarantineMessage>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val statuses = withContext(Dispatchers.IO) {
                messages.associate { message ->
                    message.id to if (restoreToInbox(app, message)) {
                        QuarantineStatus.RESTORED
                    } else {
                        QuarantineStatus.NOT_SPAM
                    }
                }
            }
            container.messageRepository.updateStatuses(statuses)
            val restored = statuses.values.count { it == QuarantineStatus.RESTORED }
            val count = messages.size
            _events.value = UiEvent.Message(
                when {
                    restored == count -> "Restored $count messages to the Messages app"
                    restored == 0 -> "Marked $count messages as not spam"
                    else -> "Restored $restored · marked ${count - restored} as not spam"
                }
            )
        }
    }

    /** Bulk variant of [restore] used by multi-select. */
    fun restore(messages: List<QuarantineMessage>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val restored = withContext(Dispatchers.IO) {
                messages.count { restoreToInbox(app, it) }
            }
            container.messageRepository.updateStatuses(
                messages.associate { it.id to QuarantineStatus.RESTORED }
            )
            val count = messages.size
            _events.value = UiEvent.Message(
                if (restored == count) "Restored $count messages to the Messages app"
                else "Marked $count messages as restored"
            )
        }
    }

    /** Deletes several quarantined messages in one write. */
    fun deleteMessages(ids: Set<String>) {
        if (ids.isEmpty()) return
        container.messageRepository.deleteAll(ids)
        _events.value = UiEvent.Message("Deleted ${ids.size} messages")
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
                val rules = container.ruleRepository.matchingRules()
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

    /**
     * Sends a message now (with the given SIM when provided) and refreshes the open
     * thread. A temporary in-memory message with [SendStatus.SENDING] appears in the
     * conversation instantly; it is updated to [SendStatus.FAILED] on error or
     * dropped once the provider refresh confirms the real row.
     */
    fun sendSms(recipient: String, body: String, subId: Int = 0) {
        if (recipient.isBlank() || body.isBlank()) return
        val address = SmsSender.parseRecipients(recipient).firstOrNull().orEmpty()
        val pendingId = -(pendingIdCounter.incrementAndGet())
        val pending = SmsMessage(
            id = pendingId,
            address = address,
            body = body,
            timestamp = System.currentTimeMillis(),
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            read = true,
            status = SendStatus.SENDING,
        )
        _pendingSends.value = _pendingSends.value + pending
        viewModelScope.launch {
            val app = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) { SmsSender.sendOutcome(app, recipient, body, subId) }
            if (outcome.ok) {
                _events.value = UiEvent.Message("Message sent")
                // Update the stub to SENT so the checkmark shows while the
                // provider reload runs.
                _pendingSends.value = _pendingSends.value.map {
                    if (it.id == pendingId) it.copy(status = SendStatus.SENT) else it
                }
                // Reload the thread so the real provider row appears, then drop the stub.
                openConversation(address)
                delay(300)
                _pendingSends.value = _pendingSends.value.filterNot { it.id == pendingId }
            } else {
                // A transient failure (no signal / airplane mode) is queued and
                // retried automatically; the stub is dropped so the queued
                // balloon in the thread is the single source of truth.
                val queued = withContext(Dispatchers.IO) {
                    SendQueue.enqueue(app, recipient, body, subId, outcome.failure, attempts = 1)
                }
                if (queued != null) {
                    _pendingSends.value = _pendingSends.value.filterNot { it.id == pendingId }
                    _events.value = UiEvent.Message("${outcome.failure.message} — queued, will retry")
                } else {
                    _events.value = UiEvent.Message("Unable to send: ${outcome.failure.message}")
                    _pendingSends.value = _pendingSends.value.map {
                        if (it.id == pendingId) it.copy(status = SendStatus.FAILED) else it
                    }
                }
            }
        }
    }

    /** Retries a failed pending send by its temp id. */
    fun retrySend(pendingId: Long, recipient: String, body: String, subId: Int = 0) {
        _pendingSends.value = _pendingSends.value.filterNot { it.id == pendingId }
        sendSms(recipient, body, subId)
    }

    /** Removes a pending send stub (dismissed failed message, etc.). */
    fun deletePendingSend(pendingId: Long) {
        _pendingSends.value = _pendingSends.value.filterNot { it.id == pendingId }
    }

    /**
     * Deletes a single message from the system provider (or removes a pending
     * send stub when the id is negative), then refreshes the open thread.
     */
    fun deleteMessage(message: SmsMessage) {
        deleteMessages(listOf(message))
    }

    /**
     * Deletes several messages at once — the provider rows and/or the in-flight
     * pending stubs — then refreshes the open thread and the conversation list.
     * Used by the conversation's multi-select mode.
     */
    fun deleteMessages(targets: List<SmsMessage>) {
        if (targets.isEmpty()) return
        val pendingIds = targets.filter { it.id < 0L }.map { it.id }.toSet()
        val providerIds = targets.filter { it.id > 0L }.map { it.id }
        if (pendingIds.isNotEmpty()) {
            _pendingSends.value = _pendingSends.value.filterNot { it.id in pendingIds }
        }
        if (providerIds.isNotEmpty()) {
            val app = appContext
            viewModelScope.launch {
                withContext(Dispatchers.IO) { SmsInbox.deleteByIds(app, providerIds) }
                refreshOpenThread()
                refreshConversations(force = true)
            }
        }
        _events.value = UiEvent.Message(
            if (targets.size == 1) "Message deleted" else "Deleted ${targets.size} messages"
        )
    }

    /** Deletes every message in the conversation with [address], then refreshes the list. */
    fun deleteConversation(address: String) {
        val threadId = _conversations.value.firstOrNull { it.address == address }?.threadId ?: 0L
        val app = appContext
        viewModelScope.launch {
            if (threadId > 0L) {
                withContext(Dispatchers.IO) { SmsThreads.deleteThread(app, threadId) }
            }
            if (openAddress == address) {
                _conversation.value = emptyList()
                _pendingSends.value = emptyList()
            }
            threadCache.evict(address)
            refreshConversations(force = true)
        }
        _events.value = UiEvent.Message("Conversation deleted")
    }

    /** Marks all incoming messages in the conversation with [address] as unread. */
    fun markConversationUnread(address: String) {
        val threadId = _conversations.value.firstOrNull { it.address == address }?.threadId ?: 0L
        if (threadId <= 0L) return
        val app = appContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SmsThreads.markThreadUnread(app, threadId) }
            refreshConversations(force = true)
        }
    }

    /** Marks the conversation with [address] as read (clears its unread badge). */
    fun markConversationRead(address: String) {
        val threadId = _conversations.value.firstOrNull { it.address == address }?.threadId ?: 0L
        val app = appContext
        viewModelScope.launch {
            if (threadId > 0L) {
                withContext(Dispatchers.IO) { SmsThreads.markThreadRead(app, threadId) }
            }
            refreshConversations(force = true)
        }
    }

    /** Deletes several conversations in one pass, then refreshes the list. */
    fun deleteConversations(addresses: Set<String>) {
        if (addresses.isEmpty()) return
        val targets = _conversations.value.filter { it.address in addresses }
        val app = appContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                targets.forEach { conversation ->
                    if (conversation.threadId > 0L) {
                        SmsThreads.deleteThread(app, conversation.threadId)
                    }
                }
            }
            if (openAddress in addresses) {
                _conversation.value = emptyList()
                _pendingSends.value = emptyList()
            }
            targets.forEach { threadCache.evict(it.address) }
            refreshConversations(force = true)
            _events.value = UiEvent.Message("Deleted ${targets.size} conversations")
        }
    }

    // --- Undo-able delete ---------------------------------------------------

    /**
     * Conversations whose deletion is pending the undo window. They are hidden
     * from the list immediately but only removed from the provider once the
     * window elapses, so a delete can be undone.
     */
    private val _pendingDeletions = MutableStateFlow<Set<String>>(emptySet())
    val pendingDeletions: StateFlow<Set<String>> = _pendingDeletions.asStateFlow()
    private var deletionJob: Job? = null

    /** Hides [addresses] now and deletes them after [DELETE_UNDO_MILLIS]. */
    fun deleteConversationsWithUndo(addresses: Set<String>) {
        if (addresses.isEmpty()) return
        _pendingDeletions.value = _pendingDeletions.value + addresses
        deletionJob?.cancel()
        deletionJob = viewModelScope.launch {
            delay(DELETE_UNDO_MILLIS)
            val targets = _pendingDeletions.value
            _pendingDeletions.value = emptySet()
            if (targets.isNotEmpty()) deleteConversations(targets)
        }
    }

    /** Cancels a pending delete, restoring the conversation(s) to the list. */
    fun undoDelete() {
        deletionJob?.cancel()
        deletionJob = null
        _pendingDeletions.value = emptySet()
    }

    /** Marks several conversations unread in one pass, then refreshes the list. */
    fun markConversationsUnread(addresses: Set<String>) {
        if (addresses.isEmpty()) return
        val threadIds = _conversations.value
            .filter { it.address in addresses }
            .map { it.threadId }
            .filter { it > 0L }
        if (threadIds.isEmpty()) return
        val app = appContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                threadIds.forEach { SmsThreads.markThreadUnread(app, it) }
            }
            refreshConversations(force = true)
            _events.value = UiEvent.Message(
                if (threadIds.size == 1) "Marked 1 conversation unread"
                else "Marked ${threadIds.size} conversations unread"
            )
        }
    }

    // --- Scheduled messages ------------------------------------------------

    /**
     * Pending or failed scheduled messages addressed to [address], for inline
     * display in that conversation. Sent and cancelled entries are excluded:
     * a sent message is already the real provider row, and a cancelled one
     * should no longer be sent.
     */
    fun scheduledFor(address: String): List<ScheduledMessage> {
        val target = SmsSender.parseRecipients(address).firstOrNull() ?: return emptyList()
        return scheduled.value
            .filter { it.status == ScheduledStatus.PENDING || it.status == ScheduledStatus.FAILED }
            .filter { SmsSender.parseRecipients(it.recipient).any { r -> r == target } }
    }

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
            val outcome = withContext(Dispatchers.IO) {
                SmsSender.sendOutcome(
                    app,
                    message.recipient,
                    message.body,
                    message.subId,
                    existingRowId = message.providerRowId.takeIf { it > 0L } ?: 0L,
                )
            }
            if (outcome.ok) {
                container.scheduleRepository.updateStatus(id, ScheduledStatus.SENT)
                _events.value = UiEvent.Message("Message sent")
                return@launch
            }
            val attempts = message.attempts + 1
            val requeued = if (outcome.failure.retriable) {
                withContext(Dispatchers.IO) {
                    SendQueue.enqueue(
                        app,
                        message.recipient,
                        message.body,
                        message.subId,
                        outcome.failure,
                        attempts,
                        message.providerRowId,
                    )
                }
            } else {
                null
            }
            if (requeued != null) {
                _events.value = UiEvent.Message("${outcome.failure.message} — will retry")
            } else {
                container.scheduleRepository.update(
                    message.copy(
                        status = ScheduledStatus.FAILED,
                        attempts = attempts,
                        lastError = outcome.failure.message,
                    )
                )
                _events.value = UiEvent.Message("Unable to send: ${outcome.failure.message}")
            }
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
        /** Upper bound on recipients in a single bulk send. */
        const val MAX_BULK_CONTACTS = 100

        /** How long the cached conversation list is trusted before a resume re-queries. */
        const val SUMMARIES_FRESH_MS = 15_000L

        /** Debounce for provider change notifications from [smsObserver]. */
        const val PROVIDER_REFRESH_DEBOUNCE_MS = 300L

        /** Pause between prefetch page reads so scrolling never starves the provider. */
        const val PREFETCH_STAGGER_MS = 50L
    }
}
