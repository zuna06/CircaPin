package com.circapin.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenUsageTest {
    private fun at(minute: Int, active: Boolean) = ScreenTransition(minute * 60_000L, active)
    @Test fun `uninterrupted usage is clipped to query window`() {
        assertEquals(30, ScreenUsage.minutes(listOf(at(-10, true), at(50, false)), 0, 30 * 60_000))
    }
    @Test fun `repeated active events do not double count`() {
        assertEquals(20, ScreenUsage.minutes(listOf(at(0, true), at(10, true), at(20, false)), 0, 60 * 60_000))
    }
    @Test fun `only screen-on intervals after dimming count`() {
        val events = listOf(at(0, true), at(10, false), at(20, true), at(40, false))
        assertEquals(15, ScreenUsage.minutes(events, 25 * 60_000, 60 * 60_000))
    }
    @Test fun `empty and reversed intervals are zero`() {
        assertEquals(0, ScreenUsage.minutes(emptyList(), 0, 60_000))
        assertEquals(0, ScreenUsage.minutes(listOf(at(0, true)), 60_000, 0))
    }
}
