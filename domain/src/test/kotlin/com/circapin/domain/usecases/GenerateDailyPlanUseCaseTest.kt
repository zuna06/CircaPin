package com.circapin.domain.usecases

import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNote
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.model.ShiftPlanner
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.at
import com.circapin.domain.usecases.PlanFixtures.goal
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.plusDays
import com.circapin.domain.usecases.PlanFixtures.profile
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateDailyPlanUseCaseTest {
    private val generate = GenerateDailyPlanUseCase()

    // Current DLMO midnight, goal 7 AM: three hours of advance over 14 nights, 13 minutes tonight.
    private val advancing = generate(model(LocalTime(0, 0)), goal(), profile(), null, TUESDAY, UTC)

    @Test
    fun `tonight's bedtime is two hours after a DLMO moved earlier by the step`() {
        assertEquals(at(TUESDAY.plusDays(1), 1, 47), advancing.plan.targetBedtime)
    }

    @Test
    fun `advancing light never starts before the temperature minimum`() {
        val temperatureMinimum = at(TUESDAY, 7, 30)
        val wake = at(TUESDAY, 10, 0)

        assertTrue(advancing.plan.brightLight.start >= temperatureMinimum)
        assertEquals(wake, advancing.plan.brightLight.start)
        assertEquals(2.hours, advancing.plan.brightLight.end - advancing.plan.brightLight.start)
    }

    @Test
    fun `an early riser gets light from the temperature minimum, not from waking`() {
        // A 5-hour sleeper wakes at 7 AM, before the 7:30 AM minimum.
        val advance = generate(model(LocalTime(0, 0)), goal(wake = LocalTime(4, 0)), profile(sleepNeedHours = 5f), null, TUESDAY, UTC)
        val steady = generate(model(LocalTime(0, 0)), goal(wake = LocalTime(7, 0)), profile(sleepNeedHours = 5f), null, TUESDAY, UTC)

        assertTrue(advance.progress.dailyStepMinutes > 0)
        assertEquals(at(TUESDAY, 7, 30), advance.plan.brightLight.start)
        assertEquals(0, steady.progress.dailyStepMinutes)
        assertEquals(at(TUESDAY, 7, 30), steady.plan.brightLight.start)
    }

    @Test
    fun `advancing avoids light in the hours before bed`() {
        assertEquals(advancing.plan.targetBedtime, advancing.plan.avoidLight.end)
        assertTrue(advancing.plan.avoidLight.start < advancing.plan.targetBedtime)
    }

    @Test
    fun `delaying moves light to the evening and avoids morning light`() {
        val day = generate(model(LocalTime(19, 0)), goal(), profile(), null, TUESDAY, UTC)

        assertTrue(day.progress.dailyStepMinutes < 0)
        assertTrue(day.plan.brightLight.end < day.plan.targetBedtime)
        assertTrue(day.plan.brightLight.start > at(TUESDAY, 18, 0))
        assertEquals(at(TUESDAY, 5, 0), day.plan.avoidLight.start)
    }

    @Test
    fun `caffeine stops early enough that under half is left by bedtime`() {
        val beforeBed = (advancing.plan.targetBedtime - advancing.plan.caffeineCutoff).toDouble(DurationUnit.HOURS)

        assertTrue(0.5.pow(beforeBed / 6) < 0.5)
    }

    @Test
    fun `bedtime never moves more than the daily cap, even for a goal tomorrow`() {
        val tomorrow = generate(model(LocalTime(2, 0)), goal(nights = 1), profile(), null, TUESDAY, UTC)
        val habitualBedtime = at(TUESDAY.plusDays(1), 4, 0)

        assertEquals(ShiftPlanner.MAX_ADVANCE_MINUTES_PER_DAY.minutes, habitualBedtime - tomorrow.plan.targetBedtime)
        assertTrue(tomorrow.progress.shortfallMinutes > 0)
    }

    @Test
    fun `a poor night halves tonight's step`() {
        val poor = SleepScore(TUESDAY, 40, 40, null, null, null, SleepScoreNote(SleepScoreNoteKind.SHORT, 240))
        val day = generate(model(LocalTime(0, 0)), goal(), profile(), poor, TUESDAY, UTC)

        assertEquals(at(TUESDAY.plusDays(1), 1, 54), day.plan.targetBedtime)
    }

    @Test
    fun `no nap is suggested without sleep debt`() {
        assertNull(advancing.plan.nap)
    }

    @Test
    fun `high debt brings a short nap in the afternoon`() {
        val day = generate(model(LocalTime(0, 0), debtHours = 5f), goal(), profile(), null, TUESDAY, UTC)
        assertNotNull(day.plan.nap)
        val nap = day.plan.nap!!

        assertEquals(20.minutes, nap.maxDuration)
        assertEquals(at(TUESDAY, 16, 0), nap.window.start)
    }

    @Test
    fun `very high debt allows a full 90-minute cycle`() {
        val day = generate(model(LocalTime(0, 0), debtHours = 9f), goal(), profile(), null, TUESDAY, UTC)

        assertEquals(90.minutes, day.plan.nap?.maxDuration)
    }

    @Test
    fun `naps never fall within six hours of bedtime`() {
        val tight = generate(model(LocalTime(0, 0), debtHours = 9f), goal(), profile(sleepNeedHours = 11f), null, TUESDAY, UTC)
        val none = generate(model(LocalTime(0, 0), debtHours = 9f), goal(), profile(sleepNeedHours = 12f), null, TUESDAY, UTC)

        val nap = tight.plan.nap!!
        assertTrue(tight.plan.targetBedtime - nap.window.end >= 6.hours)
        assertEquals(20.minutes, nap.maxDuration)
        assertNull(none.plan.nap)
    }

    @Test
    fun `meals start within an hour of waking and end two and a half hours before bed`() {
        val wake = at(TUESDAY, 10, 0)

        assertTrue(advancing.eatingWindow.firstMeal - wake in 0.minutes..1.hours)
        assertEquals(2.5.hours, advancing.plan.targetBedtime - advancing.eatingWindow.lastMeal)
    }

    @Test
    fun `the plan belongs to the goal it was made for`() {
        assertEquals(1, advancing.plan.goalId)
        assertEquals(TUESDAY, advancing.plan.date)
    }
}
