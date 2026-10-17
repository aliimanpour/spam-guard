package com.smsspam.detector.text

import com.smsspam.detector.text.MessageContent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentTest {

    @Test
    fun `normalizeDigits latinizes persian and arabic digits`() {
        assertEquals("6037997712345678", MessageContent.normalizeDigits("۶۰۳۷۹۹۷۷۱۲۳۴۵۶۷۸"))
        assertEquals("0123456789", MessageContent.normalizeDigits("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("abc 123", MessageContent.normalizeDigits("abc ۱۲۳"))
    }

    @Test
    fun `finds an http link and keeps its scheme`() {
        val tokens = MessageContent.tokens("see https://example.com/path for info")
        assertEquals(1, tokens.size)
        assertEquals(Kind.LINK, tokens.single().kind)
        assertEquals("https://example.com/path", tokens.single().value)
    }

    @Test
    fun `adds a scheme to www and bare-domain links`() {
        assertEquals("https://www.example.com", MessageContent.firstUrl("go to www.example.com"))
        assertEquals("https://shop.example.ir/x", MessageContent.firstUrl("visit shop.example.ir/x"))
    }

    @Test
    fun `trims trailing sentence punctuation from a link`() {
        assertEquals("https://example.com", MessageContent.firstUrl("link: https://example.com."))
        assertEquals("https://example.com/a", MessageContent.firstUrl("(https://example.com/a)"))
    }

    @Test
    fun `finds a card number written with separators and latin digits`() {
        val token = MessageContent.firstAccount("card 6037-9977-1234-5678 please")
        assertEquals(Kind.CARD, token?.kind)
        assertEquals("6037997712345678", token?.value)
    }

    @Test
    fun `finds a card number written with persian digits and spaces`() {
        val token = MessageContent.firstAccount("شماره کارت ۶۰۳۷ ۹۹۷۷ ۱۲۳۴ ۵۶۷۸")
        assertEquals(Kind.CARD, token?.kind)
        assertEquals("6037997712345678", token?.value)
    }

    @Test
    fun `ignores digit runs that are not 16 digits long`() {
        assertTrue(MessageContent.tokens("tracking 123456789012345678").isEmpty())
        assertTrue(MessageContent.tokens("code 09123456789").none { it.kind == Kind.CARD })
    }

    @Test
    fun `finds a sheba number and normalizes it`() {
        val token = MessageContent.firstAccount("شبا: IR820540102680020817909002")
        assertEquals(Kind.SHEBA, token?.kind)
        assertEquals("IR820540102680020817909002", token?.value)
    }

    @Test
    fun `finds a phone number as a phone token`() {
        val token = MessageContent.tokens("call me on 0912 345 6789").single()
        assertEquals(Kind.PHONE, token.kind)
        assertEquals("09123456789", token.value)
    }

    @Test
    fun `finds a persian phone number`() {
        val token = MessageContent.tokens("تماس: ۰۹۱۲۳۴۵۶۷۸۹").single()
        assertEquals(Kind.PHONE, token.kind)
        assertEquals("09123456789", token.value)
    }

    @Test
    fun `marks an otp code as an otp token`() {
        val token = MessageContent.tokens("Your verification code is 483920").single()
        assertEquals(Kind.OTP, token.kind)
        assertEquals("483920", token.value)
    }

    @Test
    fun `extracts an english otp only when a keyword is present`() {
        assertEquals("123456", MessageContent.otpCode("Your verification code is 123456"))
        assertNull(MessageContent.otpCode("just a number 123456"))
    }

    @Test
    fun `extracts a persian otp code`() {
        assertEquals("12345", MessageContent.otpCode("کد ورود شما: ۱۲۳۴۵"))
    }

    @Test
    fun `does not treat a phone number as an otp`() {
        assertNull(MessageContent.otpCode("call me on 09123456789"))
    }

    @Test
    fun `tokens are ordered and do not overlap`() {
        val body = "کد ۱۲۳۴۵ https://example.com و کارت 6037 9977 1234 5678"
        val tokens = MessageContent.tokens(body)
        assertEquals(listOf(Kind.OTP, Kind.LINK, Kind.CARD), tokens.map { it.kind })
        tokens.zipWithNext().forEach { (first, second) ->
            assertTrue(first.end <= second.start)
        }
    }
}
