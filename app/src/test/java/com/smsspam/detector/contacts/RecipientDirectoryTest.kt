package com.smsspam.detector.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientDirectoryTest {

    private val recipients = listOf(
        Recipient(number = "+989336298652", name = "حانیه"),
        Recipient(number = "0996 340 6691", name = null),
        Recipient(number = "B.Pasargad", name = null),
    )

    @Test
    fun `finds contact by name`() {
        val result = RecipientDirectory.search(recipients, "حانیه")
        assertEquals(listOf("+989336298652"), result.map { it.number })
    }

    @Test
    fun `finds contact by name regardless of case`() {
        val result = RecipientDirectory.search(recipients, "pasargad")
        assertEquals(listOf("B.Pasargad"), result.map { it.number })
    }

    @Test
    fun `finds number across country code and trunk prefix`() {
        val result = RecipientDirectory.search(recipients, "0996")
        assertEquals(listOf("0996 340 6691"), result.map { it.number })
    }

    @Test
    fun `finds number by partial digits`() {
        val result = RecipientDirectory.search(recipients, "362986")
        assertEquals(listOf("+989336298652"), result.map { it.number })
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(RecipientDirectory.search(recipients, "   ").isEmpty())
    }
}
