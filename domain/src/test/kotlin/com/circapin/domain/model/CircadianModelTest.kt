package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.SleepFixtures.UTC
import com.circapin.domain.model.SleepFixtures.eveningMinutes
import com.circapin.domain.model.SleepFixtures.night
import com.circapin.domain.model.SleepFixtures.nightAt
import com.circapin.domain.model.SleepFixtures.plusDays
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CircadianModelTest {
    private val eveningType = UserProfile(chronotypeScore = ChronotypeQuestionnaire.MIN_SCORE, onboardingComplete = true)
    private val start = LocalDate(2026, 9, 1)

    private fun dlmoMinutes(records: List<SleepRecord>, now: Instant, timeZone: TimeZone = UTC): Int =
        eveningMinutes(CircadianModel.dlmoClockTime(CircadianModel.compute(records, eveningType, now, timeZone), timeZone))

    @Test
    fun `with no sleep data the model reports the chronotype baseline`() {
        val now = Instant.parse("2026-11-06T20:00:00Z")

        val state = CircadianModel.compute(emptyList(), eveningType, now, UTC)

        assertEquals(Instant.parse("2026-11-07T00:30:00Z"), state.estimatedDlmo)
        assertEquals(0f, state.sleepDebtHours)
        assertEquals(0f, state.socialJetlagHours)
        assertEquals(0f, state.confidence)
        assertEquals(now, state.lastUpdated)
    }

    @Test
    fun `before noon the estimate is last night's, after noon it is tonight's`() {
        val morning = CircadianModel.compute(emptyList(), eveningType, Instant.parse("2026-11-06T09:00:00Z"), UTC)
        val evening = CircadianModel.compute(emptyList(), eveningType, Instant.parse("2026-11-06T13:00:00Z"), UTC)

        assertEquals(Instant.parse("2026-11-06T00:30:00Z"), morning.estimatedDlmo)
        assertEquals(Instant.parse("2026-11-07T00:30:00Z"), evening.estimatedDlmo)
    }

    @Test
    fun `sleep that has not ended yet is ignored`() {
        val now = Instant.parse("2026-11-06T13:00:00Z")
        val future = night(LocalDate(2026, 11, 6), LocalTime(23, 0))

        assertEquals(CircadianModel.compute(emptyList(), eveningType, now, UTC), CircadianModel.compute(listOf(future), eveningType, now, UTC))
    }

    /**
     * The outline's validation harness: a 30-day shift from 3 AM to 11 PM onsets over 14 days.
     * The estimate must only advance, trail the true phase by at most an hour, and settle once the shift ends.
     */
    @Test
    fun `a 30-day schedule shift is tracked without oscillating`() {
        fun onsetMinutes(day: Int) = when {
            day < 7 -> 27 * 60
            day <= 20 -> 27 * 60 - 240 * (day - 6) / 14
            else -> 23 * 60
        }
        val records = (0 until 30).map { nightAt(start.plusDays(it), onsetMinutes(it)) }

        val estimates = (0 until 30).map { day ->
            val now = records[day].wakeTime + 2.hours
            dlmoMinutes(records.take(day + 1), now)
        }

        estimates.zipWithNext().drop(6).forEachIndexed { i, (before, after) ->
            assertTrue(after <= before + 1, "day ${i + 7}: estimate moved later, $before to $after")
        }
        val lagAtEndOfShift = estimates[20] - (onsetMinutes(20) - 120)
        assertTrue(lagAtEndOfShift in 0..60, "lag at end of shift: $lagAtEndOfShift min")
        assertTrue(abs(estimates.last() - 21 * 60) <= 15, "settled at ${estimates.last()}")
    }

    @Test
    fun `a daylight saving change does not move a steady bedtime`() {
        val newYork = TimeZone.of("America/New_York")
        val records = (0 until 10).map { night(LocalDate(2026, 10, 27).plusDays(it), LocalTime(23, 0), timeZone = newYork) }
        val now = LocalDateTime(LocalDate(2026, 11, 6), LocalTime(12, 30)).toInstant(newYork)

        assertTrue(abs(dlmoMinutes(records, now, newYork) - 21 * 60) <= 2)
    }

    @Test
    fun `outputs stay within physiological bounds for irregular histories`() {
        val random = Random(42)
        repeat(200) {
            val records = (0 until 21).filter { random.nextInt(5) > 0 }.map { day ->
                nightAt(start.plusDays(day), 19 * 60 + random.nextInt(14 * 60), (2 * 60 + random.nextInt(10 * 60)).minutes)
            }
            val now = records.maxOfOrNull { it.wakeTime }?.plus(1.hours) ?: Instant.parse("2026-09-01T12:00:00Z")

            val state = CircadianModel.compute(records, eveningType, now, UTC)

            assertTrue(state.sleepDebtHours in 0f..8f * SleepDebtCalculator.WINDOW_DAYS, "debt ${state.sleepDebtHours}")
            assertTrue(state.socialJetlagHours in -12f..12f, "jetlag ${state.socialJetlagHours}")
            assertTrue(state.confidence in 0f..1f)
            assertTrue(state.estimatedDlmo - now in (-24).hours..36.hours, "DLMO ${state.estimatedDlmo} vs $now")
        }
    }
}
