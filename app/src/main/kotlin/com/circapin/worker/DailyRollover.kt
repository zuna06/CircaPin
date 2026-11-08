package com.circapin.worker

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object DailyRollover {
    /** Recalculate from wall time after every run, including delayed runs and DST. */
    fun next(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(LocalTime.of(4, 0)).atZone(zone).toInstant()
        return if (today > now) today else local.toLocalDate().plusDays(1)
            .atTime(LocalTime.of(4, 0)).atZone(zone).toInstant()
    }
}
