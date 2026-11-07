package com.circapin.domain.repository

import com.circapin.domain.entities.ScheduleGoal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    /** The goal being worked toward, or null when there is none. */
    fun observeActive(): Flow<ScheduleGoal?>

    /** Makes [goal] the active one. Any previous active goal is marked abandoned. Returns the new goal's ID. */
    suspend fun setActive(goal: ScheduleGoal): Long
}
