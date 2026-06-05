package com.fan.hwnote.app.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RepeatTypeTest {

    @Test
    fun `fromValue returns correct type for valid values`() {
        assertEquals(RepeatType.NONE, RepeatType.fromValue(0))
        assertEquals(RepeatType.DAILY, RepeatType.fromValue(1))
        assertEquals(RepeatType.WEEKLY, RepeatType.fromValue(2))
        assertEquals(RepeatType.MONTHLY, RepeatType.fromValue(3))
        assertEquals(RepeatType.YEARLY, RepeatType.fromValue(4))
    }

    @Test
    fun `fromValue returns NONE for unknown value`() {
        assertEquals(RepeatType.NONE, RepeatType.fromValue(99))
        assertEquals(RepeatType.NONE, RepeatType.fromValue(-1))
    }

    @Test
    fun `value property round-trips correctly`() {
        for (rt in RepeatType.entries) {
            assertEquals(rt, RepeatType.fromValue(rt.value))
        }
    }
}
