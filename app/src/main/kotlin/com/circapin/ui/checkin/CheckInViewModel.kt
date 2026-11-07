package com.circapin.ui.checkin

import com.circapin.DataGuard
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.notifications.PlanReminders
import com.circapin.domain.entities.CheckIn
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.ModelState
import com.circapin.domain.repository.CheckInRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.usecases.ProcessCheckInUseCase
import com.circapin.domain.usecases.RefreshTodayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class CheckInUiState(
    val loading: Boolean = true,
    val alreadyCheckedIn: Boolean = false,
    val todayCheckIn: CheckIn? = null,
    val model: ModelState? = null,
    val forecast: EnergyForecast? = null,
    val available: Boolean = true,
    val submitting: Boolean = false,
    val error: Boolean = false,
    val date: LocalDate? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val processCheckIn: ProcessCheckInUseCase,
    private val checkIns: CheckInRepository,
    private val models: ModelRepository,
    private val plans: PlanRepository,
    private val clock: Clock,
    private val reminders: PlanReminders,
    private val timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {

    private var submitting = false
    private val status = MutableStateFlow(false)
    private val error = MutableStateFlow(false)
    private val ticks = flow { while (true) { emit(clock.now()); delay(60_000) } }

    val state: StateFlow<CheckInUiState> = ticks.flatMapLatest { now ->
        val zone = timeZoneProvider.get()
        val date = RefreshTodayUseCase.planDate(now, zone)
        combine(models.observeState(), plans.observeDay(date), checkIns.observeAll(), status, error) { model, day, checks, busy, failed ->
            val checkIn = checks.find { it.date == date }
            CheckInUiState(loading = false, alreadyCheckedIn = checkIn != null, todayCheckIn = checkIn,
                model = model, forecast = day?.forecast, submitting = busy, error = failed, date = date,
                available = now.toLocalDateTime(zone).hour in 4..11 && model != null && day?.forecast != null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    fun submit(alertness: Int) {
        val s = state.value
        val timeZone = timeZoneProvider.get()
        val date = RefreshTodayUseCase.planDate(clock.now(), timeZone)
        if (s.date != date || clock.now().toLocalDateTime(timeZone).hour !in 4..11) return
        if (submitting || !s.available || s.alreadyCheckedIn || s.model == null || s.forecast == null) return
        submitting = true
        status.value = true
        viewModelScope.launch {
            try {
            val wakeHour = clock.now().toLocalDateTime(timeZone).hour
            DataGuard.mutex.withLock {
                processCheckIn(alertness, date, plans.observeDay(date).first()?.forecast ?: s.forecast, models.observeState().first() ?: s.model, wakeHour)
            }
            reminders.cancelCheckInToday()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (_: Exception) { error.value = true }
            finally { submitting = false; status.value = false }
        }
    }
}
