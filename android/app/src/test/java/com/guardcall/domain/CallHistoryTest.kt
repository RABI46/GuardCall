package com.guardcall.domain

import org.junit.Assert.*
import org.junit.Test

class CallHistoryTest {
    private fun entry(id: Int) = HistoryEntry(id = "$id", timestamp = id.toLong(), number = "33162123456",
        action = CallAction.BLOCK, reason = "Règle")
    @Test fun `newest decision is first`() {
        assertEquals(listOf(entry(2), entry(1)), CallHistory.append(listOf(entry(1)), entry(2)))
    }
    @Test fun `bounded to 500 without modifying input`() {
        val initial = (1..500).map { entry(it) }
        val result = CallHistory.append(initial, entry(501))
        assertEquals(500, result.size)
        assertEquals(entry(501), result.first())
        assertEquals(500, initial.size)
        assertFalse(result.contains(entry(500)))
    }
    @Test fun `replayed id is not duplicated`() {
        assertEquals(listOf(entry(1)), CallHistory.append(listOf(entry(1)), entry(1)))
    }
    @Test fun `withheld number can be represented without invented number`() {
        assertNull(HistoryEntry(timestamp = 0, number = null, action = CallAction.ALLOW, reason = "Masqué").number)
    }
}
