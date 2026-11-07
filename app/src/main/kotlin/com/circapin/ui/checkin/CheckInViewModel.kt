package com.circapin.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val processCheckIn: ProcessCheckInUseCase,
    private val checkIns: CheckInRepository,
    models: ModelRepository,
    plans: PlanRepository,
    private val clock: Clock,
    private val timeZoneProvider: Provider<TimeZone>,
) : ViewModel() {

    private val timeZone = timeZoneProvider.get()
    private val date = RefreshTodayUseCase.planDate(clock.now(), timeZone)
    private val submitted = MutableStateFlow<CheckIn?>(null)

    val state: StateFlow<CheckInUiState> = combine(
        models.observeState(),
        plans.observeDay(date),
        submitted,
    ) { model, day, result ->
        val existing = checkIns.get(date)
        val checkIn = result ?: existing
        CheckInUiState(
            loading = false,
            alreadyCheckedIn = checkIn != null,
            todayCheckIn = checkIn,
            model = model,
            forecast = day?.forecast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    fun submit(alertness: Int) {
        val s = state.value
        if (s.alreadyCheckedIn || s.model == null || s.forecast == null) return
        viewModelScope.launch {
            val wakeHour = clock.now().toLocalDateTime(timeZone).hour
            val result = processCheckIn(alertness, date, s.forecast, s.model, wakeHour)
            submitted.value = result
        }
    }
}
