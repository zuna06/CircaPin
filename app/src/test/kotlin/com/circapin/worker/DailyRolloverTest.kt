package com.circapin.worker

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DailyRolloverTest {
    private val ny = ZoneId.of("America/New_York")
    private fun next(now: String, expected: String, zone: ZoneId = ny) =
        assertEquals(Instant.parse(expected), DailyRollover.next(Instant.parse(now), zone))

    @Test fun `spring and fall DST keep four AM wall time`() {
        next("2026-03-07T09:00:00Z", "2026-03-08T08:00:00Z")
        next("2026-10-31T08:00:00Z", "2026-11-01T09:00:00Z")
    }

    @Test fun `delayed execution realigns rather than adding twenty four hours`() {
        next("2026-11-07T15:30:00Z", "2026-11-08T09:00:00Z")
        next("2026-11-10T18:00:00Z", "2026-11-11T09:00:00Z")
        next("2026-11-07T08:59:59Z", "2026-11-07T09:00:00Z")
    }

    @Test fun `travel recomputes the next local boundary`() {
        next("2026-11-07T10:00:00Z", "2026-11-08T09:00:00Z")
        next("2026-11-07T10:00:00Z", "2026-11-07T12:00:00Z", ZoneId.of("America/Los_Angeles"))
    }
}
