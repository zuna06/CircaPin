package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.entities.TimeWindow
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.at
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.plusDays
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComputeEnergyForecastUseCaseTest {
    private val compute = ComputeEnergyForecastUseCase()

    private fun plan(bedtime: kotlin.time.Instant) = DailyPlan(
        date = TUESDAY,
        goalId = 1,
        brightLight = TimeWindow(bedtime, bedtime),
        avoidLight = TimeWindow(bedtime, bedtime),
        caffeineCutoff = bedtime,
        targetBedtime = bedtime,
        nap = null,
    )

    /** Three nights of 11 PM to 7 AM before the plan date. */
    private val regularNights = (1..3).map { back ->
        val onset = at(TUESDAY.plusDays(-back), 23)
        SleepRecord(sleepOnset = onset, wakeTime = onset + 8.hours, isNap = false, source = SleepSource.MANUAL)
    }

    private val typical = compute(plan(at(TUESDAY, 23)), model(LocalTime(21, 0)), regularNights, 8f, UTC)

    @Test
    fun `there is one value per hour, each between 0 and 1`() {
        assertEquals(24, typical.hourlyAlertness.size)
        assertTrue(typical.hourlyAlertness.all { it in 0f..1f })
    }

    @Test
    fun `daytime is more alert than the small hours`() {
        val day = typical.hourlyAlertness.subList(10, 20).average()
        val night = typical.hourlyAlertness.subList(2, 6).average()

        assertTrue(day > night + 0.2, "day $day, night $night")
    }

    @Test
    fun `alertness peaks in the afternoon or early evening for a 9 PM DLMO`() {
        val awakeHours = 8..22
        val peak = awakeHours.maxBy { typical.hourlyAlertness[it] }

        assertTrue(peak in 13..19, "peak at $peak")
    }

    @Test
    fun `sleep debt lowers every hour`() {
        val tired = compute(plan(at(TUESDAY, 23)), model(LocalTime(21, 0), debtHours = 10f), regularNights, 8f, UTC)

        assertTrue(typical.hourlyAlertness.indices.all { tired.hourlyAlertness[it] < typical.hourlyAlertness[it] })
    }

    @Test
    fun `a later body clock pushes the peak later`() {
        val late = compute(plan(at(TUESDAY.plusDays(1), 2)), model(LocalTime(0, 0)), regularNights, 8f, UTC)
        fun peak(values: List<Float>) = (8..22).maxBy { values[it] }

        assertTrue(peak(late.hourlyAlertness) > peak(typical.hourlyAlertness))
    }

    @Test
    fun `a missing last night is assumed at the habitual time`() {
        val withoutLastNight = compute(plan(at(TUESDAY, 23)), model(LocalTime(21, 0)), regularNights.drop(1), 8f, UTC)

        assertEquals(24, withoutLastNight.hourlyAlertness.size)
        assertTrue(withoutLastNight.hourlyAlertness[12] > 0.3f)
    }
}
