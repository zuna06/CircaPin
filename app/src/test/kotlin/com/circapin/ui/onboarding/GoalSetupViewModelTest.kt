package com.circapin.ui.onboarding

import com.circapin.domain.entities.GoalReason
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.UserProfile
import com.circapin.ui.FakeGoalRepository
import com.circapin.ui.FakeModelRepository
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class GoalSetupViewModelTest {
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@GoalSetupViewModelTest.now
    }
    private val today = LocalDate(2026, 11, 10)
    private val goals = FakeGoalRepository()
    private val models = FakeModelRepository()
    private val profiles = FakeUserProfileRepository(UserProfile(chronotypeScore = 14, onboardingComplete = true))

    private fun viewModel() = GoalSetupViewModel(goals, models, profiles, clock) { TimeZone.UTC }

    @Test
    fun `defaults to 7 AM two weeks out, with tomorrow the earliest date`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertEquals(LocalTime(7, 0), state.form.wakeTime)
        assertEquals(LocalDate(2026, 11, 24), state.form.date)
        assertEquals(LocalDate(2026, 11, 11), state.earliestDate)
    }

    @Test
    fun `the preview shows the shift the goal needs`() = runTest(UnconfinedTestDispatcher()) {
        models.state.value = ModelState(Instant.parse("2026-11-11T00:00:00Z"), 0f, 0f, 1f, now)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        val preview = viewModel.state.value.preview!!
        assertEquals(180, preview.shiftMinutes)
        assertEquals(14, preview.nightsLeft)
        assertEquals(13, preview.dailyStepMinutes)
    }

    @Test
    fun `there is no preview before the model has run`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun `a date of today or earlier is refused`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.setDate(today)

        viewModel.save()

        assertTrue(viewModel.state.value.form.dateTooEarly)
        assertFalse(viewModel.state.value.form.saved)
        assertTrue(goals.history.isEmpty())
    }

    @Test
    fun `saving makes the goal active`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.setWakeTime(LocalTime(8, 15))
        viewModel.setDate(LocalDate(2027, 6, 1))
        viewModel.toggleReason(GoalReason.INTERNSHIP)

        viewModel.save()

        assertTrue(viewModel.state.value.form.saved)
        val goal = goals.active.value!!
        assertEquals(LocalTime(8, 15), goal.targetWakeTime)
        assertEquals(LocalDate(2027, 6, 1), goal.targetDate)
        assertEquals(GoalReason.INTERNSHIP, goal.reason)
        assertEquals(now, goal.createdAt)
    }

    @Test
    fun `choosing the same reason again clears it`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.toggleReason(GoalReason.FINALS)
        viewModel.toggleReason(GoalReason.FINALS)

        assertNull(viewModel.state.value.form.reason)
    }

    @Test
    fun `editing starts from the active goal`() = runTest(UnconfinedTestDispatcher()) {
        goals.active.value = ScheduleGoal(
            id = 3,
            reason = GoalReason.TRAVEL,
            targetWakeTime = LocalTime(6, 30),
            targetDate = LocalDate(2026, 12, 1),
            createdAt = now,
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }

        val form = viewModel.state.value.form
        assertEquals(LocalTime(6, 30), form.wakeTime)
        assertEquals(LocalDate(2026, 12, 1), form.date)
        assertEquals(GoalReason.TRAVEL, form.reason)
    }
}
