package com.smsspam.detector.engine

import com.smsspam.detector.model.AutoReply
import com.smsspam.detector.model.ReplyTrigger
import com.smsspam.detector.model.Workflow

/**
 * Pure matching for the automation features: decides whether an incoming
 * message should trigger an auto-reply or a keyword workflow. Kept side-effect
 * free so it is trivially unit-testable.
 */
object AutomationEngine {

    /** True when [reply] should answer a message from [sender] with [body]. */
    fun matches(reply: AutoReply, sender: String, body: String): Boolean {
        if (reply.response.isBlank()) return false
        if (reply.senderFilter.isNotBlank() && !sameNumber(reply.senderFilter, sender)) return false
        return when (reply.trigger) {
            ReplyTrigger.ANY -> true
            ReplyTrigger.CONTAINS -> reply.pattern.isNotBlank() && body.contains(reply.pattern, ignoreCase = true)
            ReplyTrigger.EXACT -> reply.pattern.isNotBlank() && body.trim().equals(reply.pattern.trim(), true)
            ReplyTrigger.REGEX -> reply.pattern.isNotBlank() && regexMatches(reply.pattern, body)
        }
    }

    /** True when a keyword workflow's keyword appears in [body]. */
    fun keywordMatches(workflow: Workflow, body: String): Boolean =
        workflow.keyword.isNotBlank() && body.contains(workflow.keyword, ignoreCase = true)

    /** Safe regex match: an invalid pattern simply never matches. */
    fun regexMatches(pattern: String, target: String): Boolean = try {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(target)
    } catch (_: Exception) {
        false
    }

    /**
     * Compares two recipients loosely. Real numbers are reduced to their last
     * 10 digits so `+98 912…`, `0098912…` and `0912…` are the same number.
     * Alphanumeric sender ids (`B.Pasargad`, `Iman Sharifi`) have no digits, so
     * they are compared as case-insensitive text instead.
     */
    fun sameNumber(a: String, b: String): Boolean {
        val na = recipientKey(a)
        val nb = recipientKey(b)
        return na.isNotEmpty() && na == nb
    }

    /** Digits of [raw] with an Iranian country code and trunk zero removed. */
    fun normalizeNumber(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("98") && digits.length > 10) digits = digits.substring(2)
        return digits.trimStart('0').takeLast(10)
    }

    /**
     * Identity key for a recipient: the normalized digits when there are any,
     * otherwise the trimmed, lowercased text. This keeps alphanumeric sender
     * ids (which reduce to no digits) distinguishable instead of collapsing
     * them all into an empty string.
     */
    fun recipientKey(raw: String): String {
        val digits = normalizeNumber(raw)
        return if (digits.isNotEmpty()) digits else raw.trim().lowercase()
    }

    /** Deduplicates recipients (by [recipientKey]) keeping the first spelling. */
    fun dedupeNumbers(numbers: List<String>): List<String> {
        val seen = HashSet<String>()
        return numbers.map { it.trim() }.filter { it.isNotEmpty() }.filter { seen.add(recipientKey(it)) }
    }
}
