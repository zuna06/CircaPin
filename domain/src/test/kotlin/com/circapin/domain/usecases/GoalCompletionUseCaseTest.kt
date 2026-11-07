package com.circapin.domain.usecases

import com.circapin.domain.entities.*
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.NOW
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.profile
import com.circapin.domain.usecases.PlanFixtures.goal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GoalCompletionUseCaseTest {
    private val goals = object : GoalRepository {
        val current = MutableStateFlow<ScheduleGoal?>(null)
        override fun observeActive() = current
        override suspend fun setActive(goal: ScheduleGoal): Long { current.value = goal; return goal.id }
        override suspend fun update(goal: ScheduleGoal) { current.value = goal }
    }
    private val complete = GoalCompletionUseCase(goals)
    @Test fun `active before deadline stays active`() = runTest { assertEquals(GoalStatus.ACTIVE, complete(goal(), TUESDAY)) }
    @Test fun `on deadline completes and stays visible for that day`() = runTest {
        val goal = goal(nights = 0)
        assertEquals(GoalStatus.COMPLETED, complete(goal, TUESDAY))
        assertEquals(GoalStatus.COMPLETED, complete(goal.copy(status = GoalStatus.COMPLETED), TUESDAY))
    }
    @Test fun `next day transitions to maintenance`() = runTest {
        assertEquals(GoalStatus.MAINTAINED, complete(goal(nights = 0).copy(status = GoalStatus.COMPLETED), LocalDate.fromEpochDays(TUESDAY.toEpochDays() + 1)))
    }
    @Test fun `abandoned and maintained goals are unchanged`() = runTest {
        for (status in listOf(GoalStatus.ABANDONED, GoalStatus.MAINTAINED)) assertEquals(status, complete(goal(nights = 0).copy(status = status), TUESDAY))
    }
}
