package com.circapin.domain.usecases

import com.circapin.domain.entities.*
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.at
import com.circapin.domain.usecases.PlanFixtures.goal
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.plusDays
import com.circapin.domain.usecases.PlanFixtures.profile
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PersonalizedPlanTest {
    private val generate = GenerateDailyPlanUseCase()
    private fun history(hour: Int, count: Int = 7) = (1..count).map {
        val onset = at(TUESDAY.plusDays(-it), hour)
        SleepRecord(sleepOnset = onset, wakeTime = onset + 8.hours, isNap = false, source = SleepSource.MANUAL)
    }
    private fun day(records: List<SleepRecord>, need: Float = 8f, nights: Int = 14) = generate(
        model(LocalTime(21, 0)).copy(lastUpdated = at(TUESDAY, 18)), goal(nights = nights), profile(need), null, TUESDAY, UTC, records)

    @Test fun `same questionnaire and model produce different schedules from different routines`() {
        val early = day(history(22))
        val late = day(history(0))
        assertTrue(early.plan.targetBedtime < late.plan.targetBedtime)
        assertEquals(7, early.plan.personalizationNights)
        assertEquals(8.hours, early.plan.sleepWindow!!.let { it.end - it.start })
        assertEquals(8.hours, late.plan.sleepWindow!!.let { it.end - it.start })
    }

    @Test fun `single outlier duplicate nap and stale log do not redefine bedtime`() {
        val regular = history(23)
        val expected = day(regular)
        val outlier = regular.last().copy(sleepOnset = regular.last().sleepOnset + 4.hours, wakeTime = regular.last().wakeTime + 4.hours)
        val nap = regular.first().copy(sleepOnset = at(TUESDAY, 13), wakeTime = at(TUESDAY, 14), isNap = true)
        val old = history(19, 20).last()
        assertEquals(expected.plan.targetBedtime, day(regular.dropLast(1) + outlier + regular.first() + nap + old).plan.targetBedtime)
    }

    @Test fun `midnight histories stay around midnight instead of noon`() {
        val records = history(23).mapIndexed { index, r -> if (index % 2 == 0) r else r.copy(sleepOnset = r.sleepOnset + 1.hours, wakeTime = r.wakeTime + 1.hours) }
        val bedtime = day(records).plan.targetBedtime.toLocalDateTime(UTC).time
        assertTrue(bedtime.hour >= 21 || bedtime.hour <= 2)
    }

    @Test fun `latest completed wake anchors today's breakfast and light`() {
        val records = history(23).toMutableList()
        records[0] = records[0].copy(wakeTime = at(TUESDAY, 9))
        val plan = day(records)
        assertEquals(at(TUESDAY, 9, 30), plan.eatingWindow.breakfast.start)
        assertTrue(plan.plan.brightLight.start >= at(TUESDAY, 9))
    }

    @Test fun `three distinct ordered meal windows fit waking day`() {
        for (need in listOf(6f, 8f, 10f)) {
            val day = day(history(23), need)
            val meals = day.eatingWindow
            assertTrue(meals.breakfast.start < meals.breakfast.end)
            assertTrue(meals.breakfast.end < meals.lunch.start)
            assertTrue(meals.lunch.end < meals.dinner.start)
            assertEquals(day.plan.targetBedtime - 2.5.hours, meals.dinner.end)
            assertEquals(need.toDouble().hours, day.plan.targetWakeTime!! - day.plan.targetBedtime)
        }
    }

    @Test fun `sparse records slow urgent shifts without shortening sleep`() {
        val sparse = day(emptyList(), nights = 1)
        val known = day(history(23), nights = 1)
        // At 23:00 baseline this goal is already on time. Use a late model to exercise the advance.
        val late = generate(model(LocalTime(1, 0)), goal(nights = 1), profile(), null, TUESDAY, UTC)
        assertEquals(30, late.plan.plannedShiftMinutes)
        assertTrue(late.progress.shortfallMinutes > 0)
        assertEquals(8.hours, late.plan.targetWakeTime!! - late.plan.targetBedtime)
        assertNotNull(sparse.plan.sleepWindow)
        assertEquals(7, known.plan.personalizationNights)
    }


    @Test fun `very late wake never produces zero meal windows or sleep before dinner`() {
        val late = history(23).first().copy(wakeTime = at(TUESDAY, 18))
        val day = day(history(23).drop(1) + late)
        assertTrue(day.plan.lateWakeAdjustment)
        assertTrue(day.eatingWindow.breakfast.end < day.eatingWindow.lunch.start)
        assertTrue(day.eatingWindow.lunch.end < day.eatingWindow.dinner.start)
        assertTrue(day.eatingWindow.dinner.end < day.plan.targetBedtime)
        assertEquals(8.hours, day.plan.targetWakeTime!! - day.plan.targetBedtime)
    }

    @Test fun `irregular routines and debt slow a shift that stable records could take faster`() {
        val stable = history(23)
        val irregular = stable.mapIndexed { i, r ->
            val delta = when (i) { 0, 1, 2 -> -2.hours; 4, 5, 6 -> 2.hours; else -> 0.hours }
            r.copy(sleepOnset = r.sleepOnset + delta, wakeTime = r.wakeTime + delta)
        }
        fun plan(records: List<SleepRecord>, debt: Float = 0f) = generate(
            model(LocalTime(21, 0), debt).copy(lastUpdated = at(TUESDAY, 18)),
            goal(wake = LocalTime(5, 0), nights = 1), profile(), null, TUESDAY, UTC, records).plan
        assertEquals(60, plan(stable).plannedShiftMinutes)
        assertEquals(30, plan(irregular).plannedShiftMinutes)
        assertEquals(30, plan(stable, 5f).plannedShiftMinutes)
    }

    @Test fun `future logs cannot influence today's plan`() {
        val stable = history(23)
        val future = stable.first().copy(sleepOnset = at(TUESDAY.plusDays(1), 4), wakeTime = at(TUESDAY.plusDays(1), 12))
        assertEquals(day(stable), day(stable + future))
    }

    @Test fun `DST preserves elapsed sleep duration and ordered meals`() {
        val zone = TimeZone.of("America/New_York")
        for (date in listOf(LocalDate(2026, 3, 7), LocalDate(2026, 10, 31))) {
            val day = generate(model(LocalTime(21, 0)), goal(), profile(), null, date, zone)
            assertEquals(8.hours, day.plan.targetWakeTime!! - day.plan.targetBedtime)
            assertEquals(30.minutes, day.plan.bedtimeWindow.end - day.plan.bedtimeWindow.start)
            assertTrue(day.eatingWindow.lunch.end < day.eatingWindow.dinner.start)
        }
    }
}
