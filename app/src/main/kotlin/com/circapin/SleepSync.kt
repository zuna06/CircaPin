package com.circapin

import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that syncs sleep from Health Connect, so every screen sees the same state.
 *
 * The app syncs each time it comes to the foreground (FR-2). [lastAttempt] lets the model and today's plan wait
 * until a sync has had its chance, whatever its outcome.
 */
@Singleton
class SleepSync @Inject constructor(
    private val repository: SleepRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _syncing = MutableStateFlow(false)
    private val _lastResult = MutableStateFlow<SleepSyncResult?>(null)
    private val _lastAttempt = MutableStateFlow<Instant?>(null)

    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Null until the first attempt finishes. */
    val lastResult: StateFlow<SleepSyncResult?> = _lastResult.asStateFlow()

    /** When the latest attempt finished. Null until the first one does. */
    val lastAttempt: StateFlow<Instant?> = _lastAttempt.asStateFlow()

    /** Overlapping requests run one after the other rather than reading Health Connect twice at once. */
    suspend fun sync(): SleepSyncResult = mutex.withLock {
        _syncing.value = true
        try {
            repository.syncFromHealthConnect().also {
                _lastResult.value = it
                _lastAttempt.value = clock.now()
            }
        } finally {
            _syncing.value = false
        }
    }
}
