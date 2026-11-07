package com.circapin.domain.usecases

import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.repository.GoalRepository
import kotlinx.datetime.LocalDate

/**
 * On or after the goal date, marks an active goal as COMPLETED and then MAINTAINED.
 * A maintained goal keeps generating plans that hold the schedule in place, until the user sets a new goal.
 */
class GoalCompletionUseCase(private val goals: GoalRepository) {

    suspend operator fun invoke(goal: ScheduleGoal, today: LocalDate): GoalStatus {
        if (goal.status != GoalStatus.ACTIVE && goal.status != GoalStatus.COMPLETED) return goal.status
        if (today.toEpochDays() < goal.targetDate.toEpochDays()) return goal.status
        if (goal.status == GoalStatus.COMPLETED && today == goal.targetDate) return goal.status

        val newStatus = if (goal.status == GoalStatus.ACTIVE) GoalStatus.COMPLETED else GoalStatus.MAINTAINED
        goals.update(goal.copy(status = newStatus))
        return newStatus
    }
}
