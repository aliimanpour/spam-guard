package com.smsspam.detector.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyEngineTest {

    @Test
    fun `persian thank you gets a persian reply`() {
        val replies = SmartReplyEngine.suggestions("ممنون از لطفت")
        assertTrue(replies.any { it.contains("خواهش") })
    }

    @Test
    fun `persian time question suggests a time reply`() {
        val replies = SmartReplyEngine.suggestions("چه ساعتی میای؟")
        assertTrue(replies.any { it.contains("الان میام") || it.contains("نیم ساعت") })
    }

    @Test
    fun `persian money message suggests a payment reply`() {
        val replies = SmartReplyEngine.suggestions("مبلغ ۲۰۰ هزار تومان رو واریز کن")
        assertTrue(replies.any { it.contains("واریز") })
    }

    @Test
    fun `english greeting gets an english reply`() {
        val replies = SmartReplyEngine.suggestions("Hello, are you there?")
        assertTrue(replies.any { it.contains("Hi") || it.contains("Hello") })
    }

    @Test
    fun `otp messages are never answered`() {
        assertTrue(SmartReplyEngine.suggestions("کد تایید شما: 75467").isEmpty())
        assertTrue(SmartReplyEngine.suggestions("Your verification code is 4821").isEmpty())
    }

    @Test
    fun `limit is respected and fallbacks are persian for persian text`() {
        val replies = SmartReplyEngine.suggestions("سلام", limit = 2)
        assertEquals(2, replies.size)
        assertTrue(replies.all { SmartReplyEngine.isPersian(it) })
    }

    @Test
    fun `plain text still gets generic fallbacks`() {
        val replies = SmartReplyEngine.suggestions("باشه فردا میبینمت")
        assertFalse(replies.isEmpty())
        assertTrue(replies.all { it.isNotBlank() })
    }
}
