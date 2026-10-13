package com.smsspam.detector.engine

import com.smsspam.detector.data.RuleRepository
import com.smsspam.detector.engine.RuleEngine.matches
import com.smsspam.detector.model.Condition
import com.smsspam.detector.model.MatchType
import com.smsspam.detector.model.Rule
import com.smsspam.detector.model.RuleLogic
import com.smsspam.detector.model.SmsField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun `default opt-out rule flags Persian لغو11`() {
        val rules = RuleRepository.defaultRules()
        val match = RuleEngine.firstMatch("+989120000000", "برای لغو11 عدد ۱۱ را ارسال کنید", rules)
        assertEquals("preset-optout", match?.id)
    }

    @Test
    fun `plain greeting is not spam`() {
        val rules = RuleRepository.defaultRules()
        assertNull(RuleEngine.firstMatch("+989120000000", "سلام، حالت چطوره؟", rules))
    }

    @Test
    fun `AND logic requires all conditions`() {
        val rule = Rule(
            name = "and",
            logic = RuleLogic.ALL,
            conditions = listOf(
                Condition(field = SmsField.SENDER, type = MatchType.CONTAINS, pattern = "bank"),
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "verify"),
            ),
        )
        assertTrue(rule.matches("Bank", "Please verify your account"))
        assertFalse(rule.matches("Bank", "Please check your account"))
    }

    @Test
    fun `OR logic needs only one condition`() {
        val rule = Rule(
            name = "or",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "winner"),
                Condition(field = SmsField.BODY, type = MatchType.CONTAINS, pattern = "prize"),
            ),
        )
        assertTrue(rule.matches("", "You are a WINNER"))
        assertFalse(rule.matches("", "Just a normal note"))
    }

    @Test
    fun `regex condition matches`() {
        val rule = Rule(
            name = "regex",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.BODY, type = MatchType.REGEX, pattern = "code\\s*\\d{4}"),
            ),
        )
        assertTrue(rule.matches("", "Your code 4821 expires soon"))
        assertFalse(rule.matches("", "Your code expires soon"))
    }

    @Test
    fun `invalid regex is reported and never matches`() {
        val condition = Condition(field = SmsField.BODY, type = MatchType.REGEX, pattern = "(unclosed")
        assertTrue(RuleEngine.hasInvalidRegex(condition))
        assertFalse(condition.matches("", "anything"))
    }

    @Test
    fun `sender conditions use the sender field`() {
        val rule = Rule(
            name = "sender",
            logic = RuleLogic.ANY,
            conditions = listOf(
                Condition(field = SmsField.SENDER, type = MatchType.EQUALS, pattern = "AD-BANK"),
            ),
        )
        assertTrue(rule.matches("ad-bank", "hello"))
        assertFalse(rule.matches("+98912", "hello"))
    }

    @Test
    fun `disabled rules are ignored`() {
        val rules = RuleRepository.defaultRules().map { it.copy(enabled = false) }
        assertNull(RuleEngine.firstMatch("", "لغو11", rules))
    }
}
