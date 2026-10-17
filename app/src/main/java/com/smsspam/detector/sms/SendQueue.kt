package com.smsspam.detector.sms

import android.content.Context
import com.smsspam.detector.SpamGuardApp
import com.smsspam.detector.model.ScheduledMessage
import com.smsspam.detector.model.ScheduledStatus

/**
 * The automatic retry queue for messages that failed because the phone had no
 * signal (or airplane mode was on). It is backed by the same store as scheduled
 * sends, so a queued message survives a restart, is re-armed after a reboot, and
 * shows up inline in its conversation as a "Queued" balloon with Retry/Delete.
 */
object SendQueue {

    /** Give up (mark the send permanently failed) after this many attempts. */
    const val MAX_ATTEMPTS = 8

    /** Growing delay between attempts, in milliseconds. */
    private val BACKOFF_MS = longArrayOf(
        30_000L,            // 30s
        2 * 60_000L,        // 2m
        5 * 60_000L,        // 5m
        15 * 60_000L,       // 15m
        30 * 60_000L,       // 30m
        60 * 60_000L,       // 1h (then every hour)
    )

    /** Delay before attempt number [attempts] (1-based). */
    fun backoffFor(attempts: Int): Long = BACKOFF_MS[(attempts - 1).coerceIn(0, BACKOFF_MS.size - 1)]

    /** True while another automatic attempt is still allowed. */
    fun canRetry(attempts: Int): Boolean = attempts < MAX_ATTEMPTS

    /** True unless the user turned automatic retry off in Settings. */
    fun enabled(context: Context): Boolean =
        (context.applicationContext as? SpamGuardApp)?.container?.settingsStore?.autoRetry ?: true

    /**
     * Queues (or bumps) an automatic retry for a message that failed with
     * [reason]. Returns the stored entry, or null when auto-retry is off, the
     * reason is not retriable, or the attempts are exhausted.
     */
    fun enqueue(
        context: Context,
        recipient: String,
        body: String,
        subId: Int,
        reason: SendFailure,
        attempts: Int,
        providerRowId: Long = 0L,
    ): ScheduledMessage? {
        if (!enabled(context) || !reason.retriable || !canRetry(attempts)) return null
        val container = (context.applicationContext as? SpamGuardApp)?.container ?: return null
        val repository = container.scheduleRepository
        val target = SmsSender.parseRecipients(recipient).firstOrNull() ?: recipient.trim()
        if (target.isBlank() || body.isBlank()) return null

        val now = System.currentTimeMillis()
        val existing = repository.scheduled.value.firstOrNull {
            (it.status == ScheduledStatus.PENDING || it.status == ScheduledStatus.FAILED) &&
                SmsSender.parseRecipients(it.recipient).firstOrNull() == target &&
                it.body == body
        }
        val rowId = providerRowId.takeIf { it > 0L } ?: existing?.providerRowId ?: 0L
        val entry = existing?.copy(
            status = ScheduledStatus.PENDING,
            sendAt = now + backoffFor(attempts),
            attempts = attempts,
            lastError = reason.message,
            providerRowId = rowId,
            subId = subId,
        ) ?: ScheduledMessage(
            recipient = target,
            body = body,
            sendAt = now + backoffFor(attempts),
            attempts = attempts,
            lastError = reason.message,
            providerRowId = rowId,
            subId = subId,
        )
        if (existing != null) repository.update(entry) else repository.add(entry)
        SmsAlarmScheduler.schedule(context, entry)
        return entry
    }
}
