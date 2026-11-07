package com.circapin.domain.entities

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepRecordTest {
    private val bedtime = Instant.parse("2026-11-06T04:00:00Z")
    private val manualNight = record(start = Duration.ZERO, end = 8.hours, source = SleepSource.MANUAL)

    private fun record(start: Duration, end: Duration, source: SleepSource = SleepSource.HEALTH_CONNECT) = SleepRecord(
        sleepOnset = bedtime + start,
        wakeTime = bedtime + end,
        isNap = false,
        source = source,
    )

    @Test
    fun `a session covering most of the night supersedes it`() {
        assertTrue(manualNight.isSupersededBy(listOf(record(30.minutes, 8.hours + 30.minutes))))
    }

    @Test
    fun `exactly half is enough`() {
        assertTrue(manualNight.isSupersededBy(listOf(record(Duration.ZERO, 4.hours))))
    }

    @Test
    fun `just under half is not`() {
        assertFalse(manualNight.isSupersededBy(listOf(record(Duration.ZERO, 4.hours - 1.minutes))))
    }

    @Test
    fun `fragments of one night count together`() {
        val fragments = listOf(record(Duration.ZERO, 2.hours), record(3.hours, 5.hours))

        assertTrue(manualNight.isSupersededBy(fragments))
    }

    @Test
    fun `a wearable that stopped early does not supersede a full night`() {
        assertFalse(manualNight.isSupersededBy(listOf(record(Duration.ZERO, 2.hours))))
    }

    @Test
    fun `a nap inside a longer session is superseded`() {
        val nap = record(2.hours, 2.hours + 30.minutes, SleepSource.MANUAL)

        assertTrue(nap.isSupersededBy(listOf(record(Duration.ZERO, 8.hours))))
    }

    @Test
    fun `sessions outside the record do not count`() {
        assertFalse(manualNight.isSupersededBy(listOf(record(9.hours, 17.hours), record((-8).hours, Duration.ZERO))))
        assertFalse(manualNight.isSupersededBy(emptyList()))
    }
}
