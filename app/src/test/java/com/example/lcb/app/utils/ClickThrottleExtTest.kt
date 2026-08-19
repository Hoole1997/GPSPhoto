package com.example.lcb.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClickThrottleExtTest {

    @Test
    fun `accepts first click and rejects clicks inside interval`() {
        var now = 1_000L
        val accepted = mutableListOf<String>()
        val throttled = { value: String -> accepted += value }
            .throttleFirst(intervalMillis = 800L, clockMillis = { now })

        throttled("first")
        now = 1_400L
        throttled("duplicate")
        now = 1_800L
        throttled("next")

        assertEquals(listOf("first", "next"), accepted)
    }

    @Test
    fun `rejects a negative interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            ({ _: Unit -> }).throttleFirst(intervalMillis = -1L)
        }
    }
}
