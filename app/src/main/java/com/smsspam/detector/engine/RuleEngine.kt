package com.smsspam.detector.engine

import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleAction
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField

/** Evaluates incoming SMS against the user's rules. */
object RuleEngine {

    /**
     * Returns the first enabled block-rule that matches, or null when the
     * message looks safe. Enabled allow-rules are evaluated first: when one
     * matches — the built-in verification-code preset, for example — the
     * message is treated as safe even though a block-rule would match.
     */
    fun firstMatch(sender: String, body: String, rules: List<Rule>): Rule? {
        val enabled = rules.filter { it.enabled }
        val allowed = enabled.any { it.action == RuleAction.ALLOW && it.matches(sender, body) }
        if (allowed) return null
        return enabled.firstOrNull { it.action == RuleAction.BLOCK && it.matches(sender, body) }
    }

    fun Rule.matches(sender: String, body: String): Boolean {
        if (conditions.isEmpty()) return false
        return when (logic) {
            RuleLogic.ANY -> conditions.any { it.matches(sender, body) }
            RuleLogic.ALL -> conditions.all { it.matches(sender, body) }
        }
    }

    fun Condition.matches(sender: String, body: String): Boolean {
        if (pattern.isEmpty()) return false
        val target = when (field) {
            SmsField.BODY -> body
            SmsField.SENDER -> sender
            SmsField.ANY -> "$sender\n$body"
        }
        val ignore = ignoreCase
        return when (type) {
            MatchType.CONTAINS -> target.contains(pattern, ignore)
            MatchType.NOT_CONTAINS -> !target.contains(pattern, ignore)
            MatchType.EQUALS -> target.equals(pattern, ignore)
            MatchType.STARTS_WITH -> target.startsWith(pattern, ignore)
            MatchType.ENDS_WITH -> target.endsWith(pattern, ignore)
            MatchType.REGEX -> regexMatches(pattern, ignore, target) == true
            MatchType.NOT_REGEX -> regexMatches(pattern, ignore, target) == false
        }
    }

    /** Returns null when the regex pattern is invalid so the UI can flag it. */
    fun regexMatches(pattern: String, ignoreCase: Boolean, target: String): Boolean? {
        val regex = compile(pattern, ignoreCase) ?: return null
        return regex.containsMatchIn(target)
    }

    fun compile(pattern: String, ignoreCase: Boolean): Regex? = try {
        Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
    } catch (_: Exception) {
        null
    }

    /** True when a pattern that claims to be a regex cannot be compiled. */
    fun hasInvalidRegex(condition: Condition): Boolean {
        if (!condition.isRegex || condition.pattern.isEmpty()) return false
        return compile(condition.pattern, condition.ignoreCase) == null
    }
}
