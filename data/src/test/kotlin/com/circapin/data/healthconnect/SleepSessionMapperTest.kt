package com.circapin.data.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord.Stage
import androidx.health.connect.client.records.metadata.Metadata
import com.circapin.domain.entities.SleepSource
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepSessionMapperTest {
    private val start = Instant.parse("2026-11-05T23:30:00Z")

    private fun session(
        hours: Long,
        stages: List<Stage> = emptyList(),
    ) = SleepSessionRecord(
        startTime = start,
        startZoneOffset = ZoneOffset.UTC,
        endTime = start.plusSeconds(hours * 3600),
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.unknownRecordingMethodWithId("hc-1"),
        stages = stages,
    )

    @Test
    fun `maps times, source and external ID across midnight`() {
        val record = session(hours = 8).toSleepRecord()

        assertEquals("hc-1", record.externalId)
        assertEquals(SleepSource.HEALTH_CONNECT, record.source)
        assertEquals(480, record.durationMinutes)
        assertEquals(start.toEpochMilli(), record.sleepOnset.toEpochMilliseconds())
        assertFalse(record.isNap)
    }

    @Test
    fun `a session up to three hours is a nap`() {
        assertTrue(session(hours = 3).toSleepRecord().isNap)
        assertFalse(session(hours = 4).toSleepRecord().isNap)
    }

    @Test
    fun `quality is null without stage data`() {
        assertNull(session(hours = 8).toSleepRecord().qualityScore)
    }

    @Test
    fun `quality is the share of the session not spent awake`() {
        val stages = listOf(
            Stage(start, start.plusSeconds(6 * 3600), SleepSessionRecord.STAGE_TYPE_DEEP),
            Stage(start.plusSeconds(6 * 3600), start.plusSeconds(8 * 3600), SleepSessionRecord.STAGE_TYPE_AWAKE),
        )

        assertEquals(0.75f, session(hours = 8, stages = stages).toSleepRecord().qualityScore)
    }
}
