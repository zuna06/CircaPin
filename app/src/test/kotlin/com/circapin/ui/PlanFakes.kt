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

    override suspend fun update(goal: ScheduleGoal) { active.value = goal; history += goal }

    override suspend fun setActive(goal: ScheduleGoal): Long {
        val saved = goal.copy(id = nextId++)
        history += saved
        active.value = saved
        return saved.id
    }
}

class FakePlanRepository : PlanRepository {
    override suspend fun deleteScore(date: LocalDate) { scores.value -= date }
    val days = MutableStateFlow<Map<LocalDate, DayPlan>>(emptyMap())
    val scores = MutableStateFlow<Map<LocalDate, SleepScore>>(emptyMap())

    override fun observeDay(date: LocalDate): Flow<DayPlan?> = days.map { it[date] }

    override fun observeScore(date: LocalDate): Flow<SleepScore?> = scores.map { it[date] }

    override suspend fun getPlan(date: LocalDate): DailyPlan? = days.value[date]?.plan

    override suspend fun saveDay(plan: DailyPlan, eatingWindow: EatingWindow, forecast: EnergyForecast) {
        days.value += plan.date to DayPlan(plan, eatingWindow, forecast)
    }

    override suspend fun saveForecast(forecast: EnergyForecast) {
        days.value[forecast.date]?.let { days.value += forecast.date to it.copy(forecast = forecast) }
    }
    override fun observeScoresSince(from: LocalDate): Flow<List<SleepScore>> = scores.map { it.values.filter { score -> score.date >= from } }

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

class FakeCheckInRepository : com.circapin.domain.repository.CheckInRepository {
    val records = MutableStateFlow<List<com.circapin.domain.entities.CheckIn>>(emptyList())
    override fun observeAll() = records
    override suspend fun get(date: LocalDate) = records.value.find { it.date == date }
    override suspend fun save(checkIn: com.circapin.domain.entities.CheckIn) {
        records.value = records.value.filterNot { it.date == checkIn.date } + checkIn
    }
}
class FakePreferencesRepository : com.circapin.domain.repository.PreferencesRepository {
    val prefs = MutableStateFlow(com.circapin.domain.entities.ReminderPreferences())
    override fun observeReminderPreferences() = prefs
    override suspend fun saveReminderPreferences(prefs: com.circapin.domain.entities.ReminderPreferences) { this.prefs.value = prefs }
}
class FakeSnapshotRepository : com.circapin.domain.repository.SnapshotRepository {
    val snapshots = MutableStateFlow<List<com.circapin.domain.repository.ModelSnapshot>>(emptyList())
    override fun observeAll() = snapshots
    override suspend fun firstSince(instant: kotlin.time.Instant) = snapshots.value.firstOrNull { it.capturedAt >= instant }
}
class FakeReminders : com.circapin.notifications.PlanReminders {
    var calls = 0
    var cancelled = false
    var preferences: com.circapin.domain.entities.ReminderPreferences? = null
    override fun schedule(plan: DailyPlan, eatingWindow: EatingWindow?, prefs: com.circapin.domain.entities.ReminderPreferences, nextWakeTime: kotlin.time.Instant, checkedIn: Boolean) { calls++; preferences = prefs }
    override fun cancelCheckInToday() { cancelled = true }
}
