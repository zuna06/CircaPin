package com.circapin.ui.sleeplog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.SleepSync
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncStatus { SYNCING, SYNCED, NOT_CONNECTED, UNAVAILABLE, FAILED }

/** @property records null until the first read from the database completes. */
data class SleepLogUiState(
    val records: List<SleepRecord>? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCING,
)

/** Shows the app-wide sync state. The app syncs on its own each time it opens, so this screen doesn't start one. */
@HiltViewModel
class SleepLogViewModel @Inject constructor(
    private val sleepSync: SleepSync,
    private val repository: SleepRepository,
) : ViewModel() {
    val state: StateFlow<SleepLogUiState> =
        combine(repository.observeRecords(), sleepSync.syncing, sleepSync.lastResult) { records, syncing, result ->
            SleepLogUiState(records, if (syncing) SyncStatus.SYNCING else result.toStatus())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepLogUiState())

    fun delete(record: SleepRecord) { viewModelScope.launch { repository.delete(record) } }

    fun sync() {
        viewModelScope.launch { sleepSync.sync() }
    }

    private fun SleepSyncResult?.toStatus() = when (this) {
        null -> SyncStatus.SYNCING
        is SleepSyncResult.Synced -> SyncStatus.SYNCED
        SleepSyncResult.NotConnected -> SyncStatus.NOT_CONNECTED
        SleepSyncResult.Unavailable -> SyncStatus.UNAVAILABLE
        SleepSyncResult.Failed -> SyncStatus.FAILED
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
