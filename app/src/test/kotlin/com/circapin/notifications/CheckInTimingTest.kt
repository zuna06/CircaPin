package com.circapin.notifications

import com.circapin.domain.entities.EatingWindow
import kotlin.time.Instant
import kotlinx.datetime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CheckInTimingTest {
    private val zone = TimeZone.of("America/New_York")
    private val date = LocalDate(2026, 11, 7)
    private fun at(time: String) = LocalDateTime(date, LocalTime.parse(time)).toInstant(zone)

    @Test fun `early and late wake reminders remain inside check-in hours`() {
        for ((wake, expected) in listOf("02:00" to "04:00", "07:00" to "07:15", "11:45" to "11:59", "12:00" to "11:59", "23:55" to "11:59")) {
            val target = CheckInTiming.target(date, at(wake), zone)
            assertEquals(at(expected), target)
            assertTrue(CheckInTiming.isOpen(target, zone))
        }
        assertFalse(CheckInTiming.isOpen(at("03:59"), zone))
        assertFalse(CheckInTiming.isOpen(at("12:00"), zone))
    }

    @Test fun `today wake comes from persisted meals even when tonight shifts`() {
        val meals = EatingWindow(date, at("07:30"), at("19:00"))
        val nextWake = Instant.parse("2026-11-08T11:00:00Z")
        assertEquals(at("07:15"), CheckInTiming.target(date, CheckInTiming.morningWake(meals, nextWake), zone))
        assertEquals(nextWake, CheckInTiming.morningWake(null, nextWake))
        val nextDate = LocalDate(2026, 11, 8)
        assertEquals(Instant.parse("2026-11-08T11:15:00Z"), CheckInTiming.target(nextDate, nextWake, zone))
    }
}
