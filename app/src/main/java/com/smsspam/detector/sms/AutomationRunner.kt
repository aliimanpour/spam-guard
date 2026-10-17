package com.smsspam.detector.sms

import android.content.Context
import android.util.Log
import com.smsspam.detector.AppContainer
import com.smsspam.detector.engine.AutomationEngine
import com.smsspam.detector.model.PendingFollowUp
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTarget
import com.smsspam.detector.model.WorkflowTrigger

/**
 * Runs the automation features against incoming and outgoing SMS:
 *  - keyword auto-replies and welcome/keyword workflows on an incoming message,
 *  - cancelling queued follow-ups when a contact replies,
 *  - queueing follow-ups after an outgoing send.
 *
 * All sends triggered here pass `triggerFollowUp = false` so automation can
 * never loop into another automation.
 */
object AutomationRunner {

    private const val TAG = "SpamGuard"

    /** Handles one allowed incoming message from [sender] with [body]. */
    fun onIncoming(context: Context, container: AppContainer, sender: String, body: String) {
        if (sender.isBlank()) return

        // The contact replied: their queued follow-ups are no longer needed.
        container.followUpRepository.removeFor(sender).forEach {
            WorkflowScheduler.cancelFollowUp(context, it.id)
        }

        runAutoReplies(context, container, sender, body)
        runIncomingWorkflows(context, container, sender, body)
    }

    private fun runAutoReplies(context: Context, container: AppContainer, sender: String, body: String) {
        container.autoReplyRepository.replies.value
            .filter { it.enabled }
            .forEach { reply ->
                if (!AutomationEngine.matches(reply, sender, body)) return@forEach
                if (reply.oncePerSender && container.automationLog.alreadyReplied(reply.id, sender)) return@forEach
                val sent = SmsSender.send(context, sender, reply.response, triggerFollowUp = false)
                if (sent) {
                    container.automationLog.recordReply(reply.id, sender)
                    Log.i(TAG, "auto-reply '${reply.name}' sent to=$sender")
                }
            }
    }

    private fun runIncomingWorkflows(context: Context, container: AppContainer, sender: String, body: String) {
        val isFirstContact = !container.automationLog.hasSeenSender(sender)
        container.automationLog.markSenderSeen(sender)

        container.workflowRepository.enabled().forEach { workflow ->
            val fires = when (workflow.trigger) {
                WorkflowTrigger.WELCOME -> isFirstContact
                WorkflowTrigger.KEYWORD -> AutomationEngine.keywordMatches(workflow, body)
                else -> false
            }
            if (fires && workflow.message.isNotBlank()) {
                val sent = SmsSender.send(context, sender, workflow.message, triggerFollowUp = false)
                Log.i(TAG, "workflow '${workflow.name}' (${workflow.trigger}) fired to=$sender sent=$sent")
            }
        }
    }

    /** Queues a follow-up for every recipient just messaged, per follow-up workflow. */
    fun onOutgoing(context: Context, container: AppContainer, recipients: List<String>) {
        if (recipients.isEmpty()) return
        val followUps = container.workflowRepository.enabled()
            .filter { it.trigger == WorkflowTrigger.FOLLOW_UP && it.message.isNotBlank() }
        if (followUps.isEmpty()) return
        val now = System.currentTimeMillis()
        followUps.forEach { workflow ->
            val delayMs = workflow.followUpDelayMinutes.coerceAtLeast(1) * 60_000L
            recipients.forEach { recipient ->
                val item = PendingFollowUp(
                    workflowId = workflow.id,
                    recipient = recipient,
                    message = workflow.message,
                    dueAt = now + delayMs,
                )
                container.followUpRepository.add(item)
                WorkflowScheduler.scheduleFollowUp(context, item)
            }
        }
    }

    /** The recipients a reminder workflow should send to. */
    fun recipientsFor(container: AppContainer, workflow: Workflow): List<String> = when (workflow.target) {
        WorkflowTarget.SENDER -> emptyList()
        WorkflowTarget.NUMBERS -> SmsSender.parseRecipients(workflow.targetValue)
        WorkflowTarget.GROUP -> container.contactsRepository.numbersInGroup(workflow.targetValue)
        WorkflowTarget.TAG -> container.contactsRepository.numbersWithTag(workflow.targetValue)
    }.let(AutomationEngine::dedupeNumbers)
}
