package com.smsspam.detector.engine

import com.smsspam.detector.model.AutoReply
import com.smsspam.detector.model.ReplyTrigger
import com.smsspam.detector.model.Workflow
import com.smsspam.detector.model.WorkflowTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    @Test
    fun `contains reply matches case-insensitively`() {
        val reply = AutoReply(trigger = ReplyTrigger.CONTAINS, pattern = "call me", response = "ok")
        assertTrue(AutomationEngine.matches(reply, "09120000000", "Please CALL ME later"))
        assertFalse(AutomationEngine.matches(reply, "09120000000", "hello there"))
    }

    @Test
    fun `any reply matches everything with a response`() {
        val reply = AutoReply(trigger = ReplyTrigger.ANY, response = "Away")
        assertTrue(AutomationEngine.matches(reply, "x", "anything"))
        assertFalse(AutomationEngine.matches(reply.copy(response = ""), "x", "anything"))
    }

    @Test
    fun `exact reply trims both sides`() {
        val reply = AutoReply(trigger = ReplyTrigger.EXACT, pattern = "status", response = "ok")
        assertTrue(AutomationEngine.matches(reply, "x", "  STATUS "))
        assertFalse(AutomationEngine.matches(reply, "x", "status please"))
    }

    @Test
    fun `regex reply never matches an invalid pattern`() {
        val bad = AutoReply(trigger = ReplyTrigger.REGEX, pattern = "(unclosed", response = "ok")
        assertFalse(AutomationEngine.matches(bad, "x", "anything"))
        val good = AutoReply(trigger = ReplyTrigger.REGEX, pattern = "order\\s*#?\\d+", response = "ok")
        assertTrue(AutomationEngine.matches(good, "x", "my ORDER 42"))
    }

    @Test
    fun `sender filter matches the same number in any format`() {
        val reply = AutoReply(
            trigger = ReplyTrigger.ANY,
            response = "ok",
            senderFilter = "+98 912 000 0000",
        )
        assertTrue(AutomationEngine.matches(reply, "09120000000", "hi"))
        assertFalse(AutomationEngine.matches(reply, "09350000000", "hi"))
    }

    @Test
    fun `keyword workflow matches its keyword`() {
        val workflow = Workflow(trigger = WorkflowTrigger.KEYWORD, keyword = "price")
        assertTrue(AutomationEngine.keywordMatches(workflow, "What is the PRICE?"))
        assertFalse(AutomationEngine.keywordMatches(workflow, "hello"))
        assertFalse(AutomationEngine.keywordMatches(workflow.copy(keyword = ""), "anything"))
    }

    @Test
    fun `dedupeNumbers keeps first spelling and drops empties`() {
        val result = AutomationEngine.dedupeNumbers(
            listOf("09120000000", "+98 912 000 0000", "", "0935 111 2222")
        )
        assertEquals(listOf("09120000000", "0935 111 2222"), result)
    }

    @Test
    fun `alphanumeric sender ids are compared as text`() {
        assertTrue(AutomationEngine.sameNumber("B.Pasargad", "b.pasargad"))
        assertTrue(AutomationEngine.sameNumber("Iman Sharifi", " Iman Sharifi "))
        assertFalse(AutomationEngine.sameNumber("Iman Sharifi", "Canada"))
        // An empty side never matches, and text never matches a real number.
        assertFalse(AutomationEngine.sameNumber("B.Pasargad", ""))
        assertFalse(AutomationEngine.sameNumber("B.Pasargad", "09120000000"))
    }

    @Test
    fun `dedupeNumbers keeps distinct alphanumeric sender ids`() {
        val result = AutomationEngine.dedupeNumbers(
            listOf("B.Pasargad", "Iman Sharifi", "B.Pasargad", "Canada")
        )
        assertEquals(listOf("B.Pasargad", "Iman Sharifi", "Canada"), result)
    }
}
