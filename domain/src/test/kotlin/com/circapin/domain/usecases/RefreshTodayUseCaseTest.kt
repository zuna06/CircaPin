package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.at
import com.circapin.domain.usecases.PlanFixtures.goal
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.plusDays
import com.circapin.domain.usecases.PlanFixtures.profile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshTodayUseCaseTest {
    private class FakePlans : PlanRepository {
        override suspend fun deleteScore(date: LocalDate) { scores.remove(date) }
        val plans = mutableMapOf<LocalDate, DailyPlan>()
        val scores = mutableMapOf<LocalDate, SleepScore>()
        var saves = 0
        val forecasts = mutableMapOf<LocalDate, EnergyForecast>()
        override suspend fun saveForecast(forecast: EnergyForecast) { forecasts[forecast.date] = forecast }
        override fun observeScoresSince(from: LocalDate): Flow<List<SleepScore>> = flowOf(scores.values.filter { it.date >= from })

        override fun observeDay(date: LocalDate): Flow<DayPlan?> = flowOf(plans[date]?.let { DayPlan(it, null, null) })
        override fun observeScore(date: LocalDate): Flow<SleepScore?> = flowOf(scores[date])
        override suspend fun getPlan(date: LocalDate) = plans[date]
        override suspend fun saveDay(plan: DailyPlan, eatingWindow: EatingWindow, forecast: EnergyForecast) {
            plans[plan.date] = plan
            forecasts[forecast.date] = forecast
            saves++
        }
        override suspend fun saveScore(score: SleepScore) {
            scores[score.date] = score
        }
    }

    private var now = at(TUESDAY, 9)
    private val clock = object : Clock {
        override fun now(): Instant = this@RefreshTodayUseCaseTest.now
    }
    private val plans = FakePlans()
    private val refresh = RefreshTodayUseCase(plans, clock)

    /** A sync that finished just before the model last ran. */
    private val synced get() = now - 1.hours
    private val lastNight = SleepRecord(
        sleepOnset = at(TUESDAY.plusDays(-1), 23),
        wakeTime = at(TUESDAY, 7),
        isNap = false,
        source = SleepSource.MANUAL,
    )

    @Test
    fun `the plan is generated once and then kept for the day`() = runTest {
        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(), synced, UTC)
        val first = plans.plans.getValue(TUESDAY)
        val oldForecast = plans.forecasts[TUESDAY]

        refresh(listOf(lastNight), profile(), model(LocalTime(22, 0)), goal(), synced, UTC)

        assertEquals(1, plans.saves)
        assertEquals(first, plans.plans.getValue(TUESDAY))
        org.junit.jupiter.api.Assertions.assertNotEquals(oldForecast, plans.forecasts[TUESDAY])
    }

    @Test
    fun `a new goal regenerates today's plan`() = runTest {
        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(id = 1), synced, UTC)

        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(id = 2, wake = LocalTime(8, 0)), synced, UTC)

        assertEquals(2, plans.saves)
        assertEquals(2, plans.plans.getValue(TUESDAY).goalId)
    }

    @Test
    fun `the next day gets a new plan`() = runTest {
        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(), synced, UTC)
        now += 24.hours

        refresh(emptyList(), profile(), model(LocalTime(0, 0)).copy(lastUpdated = now), goal(), synced, UTC)

        assertEquals(setOf(TUESDAY, TUESDAY.plusDays(1)), plans.plans.keys)
    }

    @Test
    fun `without a goal there is a score but no plan`() = runTest {
        refresh(listOf(lastNight), profile(), model(LocalTime(21, 0)), null, synced, UTC)

        assertTrue(plans.plans.isEmpty())
        assertNotNull(plans.scores[TUESDAY])
    }

    @Test
    fun `yesterday's estimate does not become today's plan`() = runTest {
        val stale = model(LocalTime(0, 0)).copy(lastUpdated = at(TUESDAY.plusDays(-1), 22))

        refresh(emptyList(), profile(), stale, goal(), synced, UTC)
        assertTrue(plans.plans.isEmpty())

        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(), synced, UTC)
        assertEquals(1, plans.saves)
    }

    @Test
    fun `the plan waits for a model computed after the latest sync`() = runTest {
        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(), null, UTC)
        refresh(emptyList(), profile(), model(LocalTime(0, 0)), goal(), now + 1.hours, UTC)
        assertTrue(plans.plans.isEmpty())

        refresh(emptyList(), profile(), model(LocalTime(0, 0)).copy(lastUpdated = now + 2.hours), goal(), now + 1.hours, UTC)
        assertEquals(1, plans.saves)
    }

    @Test
    fun `deleting the last night clears its stored score`() = runTest {
        refresh(listOf(lastNight), profile(), model(LocalTime(21, 0)), goal(), synced, UTC)
        assertNotNull(plans.scores[TUESDAY])
        refresh(emptyList(), profile(), model(LocalTime(21, 0)), goal(), synced, UTC)
        assertTrue(TUESDAY !in plans.scores)
    }

    @Test
    fun `daylight saving never moves the local four AM boundary`() {
        val zone = kotlinx.datetime.TimeZone.of("America/New_York")
        val spring = LocalDate(2026, 3, 8)
        val fall = LocalDate(2026, 11, 1)
        for (date in listOf(spring, fall)) {
            val atFour = kotlinx.datetime.LocalDateTime(date, LocalTime(4, 0)).toInstant(zone)
            assertEquals(date, RefreshTodayUseCase.planDate(atFour, zone))
            assertEquals(LocalDate.fromEpochDays(date.toEpochDays() - 1), RefreshTodayUseCase.planDate(atFour - 1.hours, zone))
        }
    }

    @Test
    fun `the plan day starts at 4 AM`() {
        assertEquals(TUESDAY.plusDays(-1), RefreshTodayUseCase.planDate(at(TUESDAY, 3, 59), UTC))
        assertEquals(TUESDAY, RefreshTodayUseCase.planDate(at(TUESDAY, 4), UTC))
    }
}
