package com.circapin.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.ShiftPlanner
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.ModelSnapshot
import com.circapin.domain.repository.SnapshotRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.RefreshTodayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ProgressUiState(
    val loading: Boolean = true,
    val goal: ScheduleGoal? = null,
    val targetDlmo: LocalTime? = null,
    val currentDlmo: LocalTime? = null,
    val startDlmo: LocalTime? = null,
    val snapshots: List<ModelSnapshot> = emptyList(),
    val dayIndex: Int = 0,
    val totalDays: Int = 0,
    val goalStatus: GoalStatus? = null,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    goals: GoalRepository,
    models: ModelRepository,
    snapshots: SnapshotRepository,
    profiles: UserProfileRepository,
    clock: Clock,
    timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {

    private val timeZone = timeZoneProvider.get()
    private val today = RefreshTodayUseCase.planDate(clock.now(), timeZone)

    val state: StateFlow<ProgressUiState> = combine(
        goals.observeActive(),
        models.observeState(),
        snapshots.observeAll(),
        profiles.observeProfile(),
    ) { goal, model, snaps, profile ->
        if (goal == null || model == null || profile == null) {
            return@combine ProgressUiState(loading = false)
        }
        val target = ShiftPlanner.targetDlmo(goal.targetWakeTime, profile.sleepNeedHours)
        val current = CircadianModel.dlmoClockTime(model, timeZone)
        val startSnap = snaps.firstOrNull()
        val startDlmo = startSnap?.let {
            CircadianModel.dlmoClockTime(
                com.circapin.domain.entities.ModelState(it.estimatedDlmo, it.sleepDebtHours, it.socialJetlagHours, it.confidence, it.capturedAt),
                timeZone,
            )
        }
        val createdDate = goal.createdAt.toLocalDateTime(timeZone).date
        val totalDays = (goal.targetDate.toEpochDays() - createdDate.toEpochDays()).toInt().coerceAtLeast(1)
        val elapsed = (today.toEpochDays() - createdDate.toEpochDays()).toInt().coerceIn(0, totalDays)

        ProgressUiState(
            loading = false,
            goal = goal,
            targetDlmo = target,
            currentDlmo = current,
            startDlmo = startDlmo,
            snapshots = snaps,
            dayIndex = elapsed,
            totalDays = totalDays,
            goalStatus = goal.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
