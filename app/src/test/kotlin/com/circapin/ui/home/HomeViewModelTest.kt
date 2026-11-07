package com.circapin.ui.home

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.UserProfile
import com.circapin.ui.FakeGoalRepository
import com.circapin.ui.FakeModelRepository
import com.circapin.ui.FakePlanRepository
import com.circapin.ui.FakeUserProfileRepository
import com.circapin.ui.MainDispatcherExtension
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class HomeViewModelTest {
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@HomeViewModelTest.now
    }
    private val goals = FakeGoalRepository()
    private val models = FakeModelRepository()
    private val plans = FakePlanRepository()
    private val profiles = FakeUserProfileRepository(UserProfile(chronotypeScore = 14, onboardingComplete = true))

    private fun viewModel() = HomeViewModel(goals, models, plans, profiles, com.circapin.ui.FakeCheckInRepository(), clock) { TimeZone.UTC }

    @Test
    fun `without a goal there is nothing to measure against`() = runTest(UnconfinedTestDispatcher()) {
        models.state.value = ModelState(Instant.parse("2026-11-11T00:00:00Z"), 0f, 0f, 1f, now)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertNull(state.goal)
        assertNull(state.progress)
    }

    @Test
    fun `progress follows the latest estimate`() = runTest(UnconfinedTestDispatcher()) {
        goals.active.value = ScheduleGoal(id = 1, targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 13), createdAt = now)
        models.state.value = ModelState(Instant.parse("2026-11-11T02:00:00Z"), 0f, 0f, 1f, now)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        assertEquals(300, viewModel.state.value.progress?.shiftMinutes)
        assertEquals(120, viewModel.state.value.progress?.shortfallMinutes)

        models.state.value = ModelState(Instant.parse("2026-11-10T22:00:00Z"), 0f, 0f, 1f, now)

        assertEquals(60, viewModel.state.value.progress?.shiftMinutes)
        assertEquals(0, viewModel.state.value.progress?.shortfallMinutes)
    }
}
