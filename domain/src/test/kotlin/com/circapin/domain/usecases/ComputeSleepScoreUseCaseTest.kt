package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.entities.TimeWindow
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.at
import com.circapin.domain.usecases.PlanFixtures.plusDays
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComputeSleepScoreUseCaseTest {
    private val score = ComputeSleepScoreUseCase()

    private fun night(daysBack: Int, onsetOffset: Duration = Duration.ZERO, length: Duration = 8.hours, quality: Float? = null): SleepRecord {
        val onset = at(TUESDAY.plusDays(-daysBack), 23) + onsetOffset
        return SleepRecord(sleepOnset = onset, wakeTime = onset + length, isNap = false, source = SleepSource.MANUAL, qualityScore = quality)
    }

    private val week = (2..7).map { night(it) }

    private fun planWithBedtime(bedtime: Instant) = DailyPlan(
        date = TUESDAY.plusDays(-1),
        goalId = 1,
        brightLight = TimeWindow(bedtime, bedtime),
        avoidLight = TimeWindow(bedtime, bedtime),
        caffeineCutoff = bedtime,
        targetBedtime = bedtime,
        nap = null,
    )

    private val lastNightPlan = planWithBedtime(at(TUESDAY.plusDays(-1), 23))

    @Test
    fun `a full, on-time, regular night scores at the top`() {
        val result = score(week + night(1, quality = 0.97f), TUESDAY, lastNightPlan, 8f, UTC)!!

        assertTrue(result.overall >= 95, "${result.overall}")
        assertEquals(SleepScoreNoteKind.ON_TRACK, result.note.kind)
    }

    @Test
    fun `a short night is named as the problem`() {
        val result = score(week + night(1, length = 6.hours), TUESDAY, lastNightPlan, 8f, UTC)!!

        assertEquals(75, result.duration)
        assertEquals(SleepScoreNoteKind.SHORT, result.note.kind)
        assertEquals(120, result.note.minutes)
    }

    @Test
    fun `a late bedtime is measured against last night's plan`() {
        val result = score(week + night(1, onsetOffset = 40.minutes), TUESDAY, lastNightPlan, 8f, UTC)!!

        assertEquals(SleepScoreNoteKind.LATE, result.note.kind)
        assertEquals(40, result.note.minutes)
        assertTrue(result.overall in 70..90, "${result.overall}")
    }

    @Test
    fun `without a plan, timing is left out rather than guessed`() {
        val result = score(week + night(1), TUESDAY, null, 8f, UTC)!!

        assertNull(result.timing)
        assertEquals(100, result.duration)
    }

    @Test
    fun `broken sleep shows in fragmentation`() {
        val result = score(week + night(1, quality = 0.6f), TUESDAY, lastNightPlan, 8f, UTC)!!

        assertEquals(SleepScoreNoteKind.BROKEN, result.note.kind)
        assertTrue(result.fragmentation!! < 50)
    }

    @Test
    fun `there is no score until last night is logged`() {
        assertNull(score(week, TUESDAY, lastNightPlan, 8f, UTC))
    }
}
