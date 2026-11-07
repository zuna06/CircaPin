package com.circapin

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.ui.FakeGoalRepository
import com.circapin.ui.FakeModelRepository
import com.circapin.ui.FakePlanRepository
import com.circapin.ui.FakeSleepRepository
import com.circapin.ui.FakeUserProfileRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayUpdaterTest {
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@TodayUpdaterTest.now
    }
    private val profiles = FakeUserProfileRepository()
    private val models = FakeModelRepository()
    private val goals = FakeGoalRepository()
    private val plans = FakePlanRepository()
    private val sleep = FakeSleepRepository()
    private val sleepSync = SleepSync(sleep, clock)
    private val updater = TodayUpdater(
        sleep,
        profiles,
        models,
        goals,
        sleepSync,
        RefreshTodayUseCase(plans, clock),
    ) { TimeZone.UTC }

    @Test
    fun `today's plan waits for a model computed after the sync`() = runTest(UnconfinedTestDispatcher()) {
        updater.start(backgroundScope, tick = 1.days)
        profiles.profile.value = UserProfile(chronotypeScore = 14, onboardingComplete = true)
        goals.setActive(ScheduleGoal(targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 24), createdAt = now))
        sleepSync.sync()
        assertTrue(plans.days.value.isEmpty(), "no model since the sync yet")

        models.state.value = ModelState(Instant.parse("2026-11-11T00:00:00Z"), 0f, 0f, 1f, now)
        assertTrue(plans.days.value.isNotEmpty())
        goals.setActive(ScheduleGoal(targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 24), createdAt = now))

        val day = plans.days.value.getValue(LocalDate(2026, 11, 10))
        assertEquals(goals.active.value?.id, day.plan.goalId)
        assertEquals(24, day.forecast?.hourlyAlertness?.size)
    }
}
