package com.smsspam.detector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.engine.RuleEngine
import com.smsspam.detector.model.QuarantineMessage
import com.smsspam.detector.model.QuarantineStatus
import com.smsspam.detector.model.Rule
import com.smsspam.detector.sms.SmsInbox
import com.smsspam.detector.sms.restoreToInbox
import kotlinx.coroutines.Dispatchers
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
}
