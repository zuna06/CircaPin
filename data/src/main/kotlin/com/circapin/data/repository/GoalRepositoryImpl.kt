package com.circapin.data.repository

import com.circapin.data.local.ScheduleGoalDao
import com.circapin.data.local.toDomain
import com.circapin.data.local.toEntity
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepositoryImpl(private val dao: ScheduleGoalDao) : GoalRepository {
    override fun observeActive(): Flow<ScheduleGoal?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun setActive(goal: ScheduleGoal): Long = dao.replaceActive(goal.toEntity())
}
