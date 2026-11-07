package com.circapin.ui

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeGoalRepository : GoalRepository {
    val active = MutableStateFlow<ScheduleGoal?>(null)
    val history = mutableListOf<ScheduleGoal>()
    private var nextId = 1L

    override fun observeActive(): Flow<ScheduleGoal?> = active

    override suspend fun setActive(goal: ScheduleGoal): Long {
        val saved = goal.copy(id = nextId++)
        history += saved
        active.value = saved
        return saved.id
    }
}

class FakePlanRepository : PlanRepository {
    val days = MutableStateFlow<Map<LocalDate, DayPlan>>(emptyMap())
    val scores = MutableStateFlow<Map<LocalDate, SleepScore>>(emptyMap())

    override fun observeDay(date: LocalDate): Flow<DayPlan?> = days.map { it[date] }

    override fun observeScore(date: LocalDate): Flow<SleepScore?> = scores.map { it[date] }

    override suspend fun getPlan(date: LocalDate): DailyPlan? = days.value[date]?.plan

    override suspend fun saveDay(plan: DailyPlan, eatingWindow: EatingWindow, forecast: EnergyForecast) {
        days.value += plan.date to DayPlan(plan, eatingWindow, forecast)
    }

    override suspend fun saveScore(score: SleepScore) {
        scores.value += score.date to score
    }
}

class FakeUserProfileRepository(profile: UserProfile? = null) : UserProfileRepository {
    val profile = MutableStateFlow(profile)

    override fun observeProfile(): Flow<UserProfile?> = profile

    override suspend fun save(profile: UserProfile) {
        this.profile.value = profile
    }
}
