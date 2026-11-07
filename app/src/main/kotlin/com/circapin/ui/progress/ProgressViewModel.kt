package com.circapin.ui.progress

import com.circapin.planDays
import kotlinx.coroutines.flow.flatMapLatest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.ShiftPlanner
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
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
    val sleepDebtHours: Float = 0f,
    val socialJetlagHours: Float = 0f,
    val averageScore: Int? = null,
    val shiftProgress: Float = 0f,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    goals: GoalRepository,
    models: ModelRepository,
    snapshots: SnapshotRepository,
    profiles: UserProfileRepository,
    plans: PlanRepository,
    clock: Clock,
    timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {

    val state: StateFlow<ProgressUiState> = planDays(clock, timeZoneProvider).flatMapLatest { (today, timeZone) -> combine(
        goals.observeActive(),
        models.observeState(),
        snapshots.observeAll(),
        profiles.observeProfile(),
        plans.observeScoresSince(kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() - 6)),
    ) { goal, model, snaps, profile, scores ->
        if (goal == null || model == null || profile == null) {
            return@combine ProgressUiState(loading = false)
        }
        val target = ShiftPlanner.targetDlmo(goal.targetWakeTime, profile.sleepNeedHours)
        val current = CircadianModel.dlmoClockTime(model, timeZone)
        val goalSnaps = snaps.filter { it.capturedAt >= goal.createdAt }
        val startSnap = snaps.lastOrNull { it.capturedAt <= goal.createdAt } ?: goalSnaps.firstOrNull()
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
            snapshots = goalSnaps,
            dayIndex = elapsed,
            totalDays = totalDays,
            goalStatus = goal.status,
            sleepDebtHours = model.sleepDebtHours,
            socialJetlagHours = model.socialJetlagHours,
            averageScore = scores.filter { it.date <= today }.takeIf { it.isNotEmpty() }?.map { it.overall }?.average()?.toInt(),
            shiftProgress = startDlmo?.let { start ->
                val initial = kotlin.math.abs(clockDistance(start, target))
                val remaining = kotlin.math.abs(clockDistance(current, target))
                if (initial <= 15) (if (remaining <= 15) 1f else 0f) else (1 - remaining / initial).toFloat().coerceIn(0f, 1f)
            } ?: 0f,
        )
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}

private fun clockDistance(a: LocalTime, b: LocalTime): Double {
    val delta = ((a.hour * 60 + a.minute - b.hour * 60 - b.minute + 2160) % 1440) - 720
    return delta.toDouble()
}
