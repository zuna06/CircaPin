package com.circapin.ui.home

import com.circapin.planDays
import kotlinx.coroutines.flow.flatMapLatest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.CheckIn
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.GoalProgress
import com.circapin.domain.model.ShiftPlanner
import com.circapin.domain.repository.CheckInRepository
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.RefreshTodayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

/**
 * @property progress live distance to the goal. It can change during the day, unlike the plan in [day].
 */
data class HomeUiState(
    val loading: Boolean = true,
    val goal: ScheduleGoal? = null,
    val progress: GoalProgress? = null,
    val model: ModelState? = null,
    val day: DayPlan? = null,
    val score: SleepScore? = null,
    val todayCheckIn: CheckIn? = null,
    val goalStatus: GoalStatus? = null,
    val sleepNeedHours: Float = 8f,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    goals: GoalRepository,
    models: ModelRepository,
    plans: PlanRepository,
    profiles: UserProfileRepository,
    checkIns: CheckInRepository,
    clock: Clock,
    timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = planDays(clock, timeZoneProvider).flatMapLatest { (date, timeZone) -> combine(
        goals.observeActive(),
        models.observeState(),
        plans.observeDay(date),
        plans.observeScore(date),
        combine(profiles.observeProfile(), checkIns.observeAll().map { list -> list.find { it.date == date } }) { p, c -> p to c },
    ) { goal, model, day, score, (profile, checkIn) ->
        val progress = if (goal != null && model != null && profile != null) {
            ShiftPlanner.progress(CircadianModel.dlmoClockTime(model, timeZone), goal, profile.sleepNeedHours, date)
        } else {
            null
        }
        HomeUiState(
            loading = false,
            goal = goal,
            progress = progress,
            model = model,
            day = day,
            score = score,
            todayCheckIn = checkIn,
            goalStatus = goal?.status,
            sleepNeedHours = profile?.sleepNeedHours ?: 8f,
        )
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
