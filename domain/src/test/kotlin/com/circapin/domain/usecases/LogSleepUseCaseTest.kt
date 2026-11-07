package com.circapin.domain.usecases

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogSleepUseCaseTest {
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@LogSleepUseCaseTest.now
    }

    private class FakeRepository(val overlaps: Boolean = false) : SleepRepository {
        val added = mutableListOf<SleepRecord>()

        override fun observeRecords(): Flow<List<SleepRecord>> = flowOf(added)
        override suspend fun add(record: SleepRecord) { added += record }
        override suspend fun delete(record: SleepRecord) { added -= record }
        override suspend fun hasOverlap(start: Instant, end: Instant) = overlaps
        override suspend fun healthConnectStatus() = HealthConnectStatus.NOT_CONNECTED
        override suspend fun syncFromHealthConnect() = SleepSyncResult.NotConnected
    }

    @Test
    fun `a valid night is saved as a manual record`() = runTest {
        val repository = FakeRepository()

        val result = LogSleepUseCase(repository, clock)(now - 12.hours, now - 4.hours, isNap = false)

        assertEquals(LogSleepResult.Saved, result)
        val saved = repository.added.single()
        assertEquals(SleepSource.MANUAL, saved.source)
        assertEquals(480, saved.durationMinutes)
    }

    @Test
    fun `a night crossing midnight is accepted`() = runTest {
        val onset = Instant.parse("2026-11-05T22:30:00Z")
        val wake = Instant.parse("2026-11-06T09:00:00Z")

        assertEquals(LogSleepResult.Saved, LogSleepUseCase(FakeRepository(), clock)(onset, wake, isNap = false))
    }

    @Test
    fun `wake time must be after sleep onset`() = runTest {
        assertRejected(SleepEntryProblem.WAKE_NOT_AFTER_ONSET, now - 2.hours, now - 3.hours)
        assertRejected(SleepEntryProblem.WAKE_NOT_AFTER_ONSET, now - 2.hours, now - 2.hours)
    }

    @Test
    fun `an entry cannot end in the future`() = runTest {
        assertRejected(SleepEntryProblem.IN_FUTURE, now - 1.hours, now + 1.minutes)
    }

    @Test
    fun `sessions beyond the limits are rejected`() = runTest {
        assertRejected(SleepEntryProblem.TOO_LONG, now - 19.hours, now)
        assertRejected(SleepEntryProblem.TOO_LONG, now - 4.hours, now, isNap = true)
    }

    @Test
    fun `an entry overlapping an existing record is rejected`() = runTest {
        assertRejected(SleepEntryProblem.OVERLAPS_EXISTING, now - 8.hours, now, overlaps = true)
    }

    private suspend fun assertRejected(
        problem: SleepEntryProblem,
        onset: Instant,
        wake: Instant,
        isNap: Boolean = false,
        overlaps: Boolean = false,
    ) {
        val repository = FakeRepository(overlaps)

        val result = LogSleepUseCase(repository, clock)(onset, wake, isNap)

        assertEquals(LogSleepResult.Rejected(problem), result)
        assertTrue(repository.added.isEmpty())
    }
}
