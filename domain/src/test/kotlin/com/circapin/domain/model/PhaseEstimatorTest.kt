package com.circapin.domain.model

import com.circapin.domain.model.SleepFixtures.UTC
import com.circapin.domain.model.SleepFixtures.eveningMinutes
import com.circapin.domain.model.SleepFixtures.night
import com.circapin.domain.model.SleepFixtures.plusDays
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhaseEstimatorTest {
    private val monday = LocalDate(2026, 11, 2)
    private val baseline = LocalTime(21, 0)

    private fun estimate(nights: List<com.circapin.domain.entities.SleepRecord>, today: LocalDate) =
        PhaseEstimator.estimate(SleepDays.from(nights, UTC), baseline, today, UTC)

    private fun assertNear(expected: LocalTime, actual: LocalTime, toleranceMinutes: Int) {
        val gap = abs(eveningMinutes(expected) - eveningMinutes(actual))
        assertTrue(gap <= toleranceMinutes, "expected $expected within $toleranceMinutes min, was $actual")
    }

    @Test
    fun `with no nights the baseline is the estimate`() {
        val result = estimate(emptyList(), monday)

        assertEquals(baseline, result.dlmo)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `seven nights converge on two hours before onset`() {
        val nights = (0 until 7).map { night(monday.plusDays(it), LocalTime(1, 30)) }

        val result = estimate(nights, monday.plusDays(7))

        assertNear(LocalTime(23, 30), result.dlmo, toleranceMinutes = 10)
        assertEquals(1f, result.confidence)
    }

    @Test
    fun `one night moves the estimate partway from the baseline`() {
        val result = estimate(listOf(night(monday, LocalTime(1, 30))), monday.plusDays(1))
        val minutes = eveningMinutes(result.dlmo)

        assertTrue(minutes > eveningMinutes(baseline) && minutes < eveningMinutes(LocalTime(23, 30)), "${result.dlmo}")
    }

    @Test
    fun `onsets either side of midnight average to midnight, not noon`() {
        val nights = (0 until 8).map { day ->
            night(monday.plusDays(day), if (day % 2 == 0) LocalTime(23, 30) else LocalTime(0, 30))
        }

        assertNear(LocalTime(22, 0), estimate(nights, monday.plusDays(8)).dlmo, toleranceMinutes = 15)
    }

    @Test
    fun `free nights pull the estimate toward the weekend pattern`() {
        val nights = (0 until 14).map { day ->
            val date = monday.plusDays(day)
            val free = SleepDay(date, null, 0.minutes).isFreeNight
            night(date, if (free) LocalTime(3, 0) else LocalTime(0, 0))
        }

        val result = estimate(nights, monday.plusDays(14))
        val schoolNightDlmo = eveningMinutes(LocalTime(22, 0))
        val freeNightDlmo = eveningMinutes(LocalTime(1, 0))
        val unweightedDlmo = schoolNightDlmo + (freeNightDlmo - schoolNightDlmo) * 4 / 14

        assertTrue(eveningMinutes(result.dlmo) > unweightedDlmo, "${result.dlmo}")
    }

    @Test
    fun `nights older than two weeks are ignored`() {
        val old = (0 until 7).map { night(monday.plusDays(it), LocalTime(4, 0)) }
        val recent = (14 until 21).map { night(monday.plusDays(it), LocalTime(23, 0)) }

        assertNear(LocalTime(21, 0), estimate(old + recent, monday.plusDays(21)).dlmo, toleranceMinutes = 5)
    }

    @Test
    fun `naps do not move the estimate`() {
        val nights = (0 until 7).map { night(monday.plusDays(it), LocalTime(23, 0)) }
        val naps = (0 until 7).map { night(monday.plusDays(it + 1), LocalTime(15, 0), length = 30.minutes) }

        assertEquals(estimate(nights, monday.plusDays(7)).dlmo, estimate(nights + naps, monday.plusDays(7)).dlmo)
    }
}
