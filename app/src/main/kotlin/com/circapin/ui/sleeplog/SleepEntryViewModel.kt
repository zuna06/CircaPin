package com.circapin.ui.sleeplog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.usecases.LogSleepResult
import com.circapin.domain.usecases.LogSleepUseCase
import com.circapin.domain.usecases.SleepEntryProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

const val SLEEP_ENTRY_IS_NAP_ARG = "isNap"

data class SleepEntryUiState(
    val isNap: Boolean,
    val onsetDate: LocalDate,
    val onsetTime: LocalTime,
    val wakeDate: LocalDate,
    val wakeTime: LocalTime,
    val problem: SleepEntryProblem? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class SleepEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val logSleep: LogSleepUseCase,
    clock: Clock,
    private val timeZone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState(savedStateHandle[SLEEP_ENTRY_IS_NAP_ARG] ?: false, clock.now()))
    val state: StateFlow<SleepEntryUiState> = _state.asStateFlow()

    private fun initialState(isNap: Boolean, now: Instant): SleepEntryUiState {
        val wake = now.toLocalDateTime(timeZone)
        val onset = (now - if (isNap) DEFAULT_NAP_LENGTH else DEFAULT_NIGHT_LENGTH).toLocalDateTime(timeZone)
        return SleepEntryUiState(
            isNap = isNap,
            onsetDate = onset.date,
            onsetTime = LocalTime(onset.hour, onset.minute),
            wakeDate = wake.date,
            wakeTime = LocalTime(wake.hour, wake.minute),
        )
    }

    fun setOnsetDate(date: LocalDate) = edit { it.copy(onsetDate = date) }

    fun setOnsetTime(time: LocalTime) = edit { it.copy(onsetTime = time) }

    fun setWakeDate(date: LocalDate) = edit { it.copy(wakeDate = date) }

    fun setWakeTime(time: LocalTime) = edit { it.copy(wakeTime = time) }

    private fun edit(change: (SleepEntryUiState) -> SleepEntryUiState) {
        _state.update { change(it).copy(problem = null) }
    }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            val result = logSleep(
                onset = LocalDateTime(current.onsetDate, current.onsetTime).toInstant(timeZone),
                wake = LocalDateTime(current.wakeDate, current.wakeTime).toInstant(timeZone),
                isNap = current.isNap,
            )
            _state.update {
                when (result) {
                    LogSleepResult.Saved -> it.copy(isSaved = true)
                    is LogSleepResult.Rejected -> it.copy(problem = result.problem)
                }
            }
        }
    }

    private companion object {
        val DEFAULT_NIGHT_LENGTH = 8.hours
        val DEFAULT_NAP_LENGTH = 30.minutes
    }
}
