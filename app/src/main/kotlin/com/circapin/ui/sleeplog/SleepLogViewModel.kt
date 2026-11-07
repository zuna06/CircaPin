package com.circapin.ui.sleeplog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncStatus { SYNCING, SYNCED, NOT_CONNECTED, UNAVAILABLE, FAILED }

/**
 * @property records null until the first read from the database completes.
 * @property model null until the circadian model has run.
 */
data class SleepLogUiState(
    val records: List<SleepRecord>? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCING,
    val model: ModelState? = null,
)

@HiltViewModel
class SleepLogViewModel @Inject constructor(
    private val repository: SleepRepository,
    models: ModelRepository,
) : ViewModel() {
    private val syncStatus = MutableStateFlow(SyncStatus.SYNCING)

    val state: StateFlow<SleepLogUiState> =
        combine(repository.observeRecords(), syncStatus, models.observeState(), ::SleepLogUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepLogUiState())

    init {
        sync()
    }

    fun sync() {
        syncStatus.value = SyncStatus.SYNCING
        viewModelScope.launch {
            syncStatus.value = when (repository.syncFromHealthConnect()) {
                is SleepSyncResult.Synced -> SyncStatus.SYNCED
                SleepSyncResult.NotConnected -> SyncStatus.NOT_CONNECTED
                SleepSyncResult.Unavailable -> SyncStatus.UNAVAILABLE
                SleepSyncResult.Failed -> SyncStatus.FAILED
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
