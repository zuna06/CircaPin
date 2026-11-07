package com.circapin.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.GoalReason
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.GoalProgress
import com.circapin.domain.model.ShiftPlanner
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.RefreshTodayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class GoalForm(
    val wakeTime: LocalTime,
    val date: LocalDate,
    val reason: GoalReason? = null,
    val dateTooEarly: Boolean = false,
    val saved: Boolean = false,
)

/** @property preview what the goal means for the user's body clock, once the model has an estimate. */
data class GoalSetupUiState(val form: GoalForm, val earliestDate: LocalDate, val preview: GoalProgress? = null)

@HiltViewModel
class GoalSetupViewModel @Inject constructor(
    private val goals: GoalRepository,
    models: ModelRepository,
    profiles: UserProfileRepository,
    private val clock: Clock,
    timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {
    private val timeZone = timeZoneProvider.get()
    private val today = RefreshTodayUseCase.planDate(clock.now(), timeZone)
    private val tomorrow = LocalDate.fromEpochDays(today.toEpochDays() + 1)
    private val form = MutableStateFlow(
        GoalForm(wakeTime = DEFAULT_WAKE, date = LocalDate.fromEpochDays(today.toEpochDays() + DEFAULT_DAYS_AHEAD)),
    )

    val state: StateFlow<GoalSetupUiState> =
        combine(form, models.observeState(), profiles.observeProfile()) { form, model, profile ->
            val preview = if (model != null && profile != null && form.date >= tomorrow) {
                val draft = ScheduleGoal(targetWakeTime = form.wakeTime, targetDate = form.date, createdAt = clock.now())
                ShiftPlanner.progress(CircadianModel.dlmoClockTime(model, timeZone), draft, profile.sleepNeedHours, today)
            } else {
                null
            }
            GoalSetupUiState(form, tomorrow, preview)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GoalSetupUiState(form.value, tomorrow))

    init {
        // Editing an existing goal starts from its values.
        viewModelScope.launch {
            goals.observeActive().first()?.let { goal ->
                form.update { it.copy(wakeTime = goal.targetWakeTime, date = maxOf(goal.targetDate, tomorrow), reason = goal.reason) }
            }
        }
    }

    fun setWakeTime(time: LocalTime) = form.update { it.copy(wakeTime = time) }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date, dateTooEarly = false) }

    /** Selecting the current reason again clears it, since a reason is optional. */
    fun toggleReason(reason: GoalReason) = form.update { it.copy(reason = if (it.reason == reason) null else reason) }

    fun save() {
        val current = form.value
        if (current.date < tomorrow) {
            form.update { it.copy(dateTooEarly = true) }
            return
        }
        viewModelScope.launch {
            goals.setActive(
                ScheduleGoal(
                    reason = current.reason,
                    targetWakeTime = current.wakeTime,
                    targetDate = current.date,
                    createdAt = clock.now(),
                ),
            )
            form.update { it.copy(saved = true) }
        }
    }

    private companion object {
        val DEFAULT_WAKE = LocalTime(7, 0)
        const val DEFAULT_DAYS_AHEAD = 14
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
