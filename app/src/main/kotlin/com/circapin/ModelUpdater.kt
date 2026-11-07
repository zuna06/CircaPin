package com.circapin

import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.UpdateModelUseCase
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone

/**
 * Recomputes the circadian model whenever sleep records or the profile change: syncs, manual entries,
 * deletions, and replaced records all pass through here. It also runs after every sync attempt, even one that
 * changed nothing, so today's plan knows the estimate reflects the latest sync.
 */
@Singleton
class ModelUpdater @Inject constructor(
    private val sleep: SleepRepository,
    private val profiles: UserProfileRepository,
    private val sleepSync: SleepSync,
    private val updateModel: UpdateModelUseCase,
    private val timeZone: Provider<TimeZone>,
) {
    fun start(scope: CoroutineScope): Job =
        combine(sleep.observeRecords(), profiles.observeProfile(), sleepSync.lastAttempt) { records, profile, _ ->
            records to profile
        }
            .onEach { (records, profile) ->
                if (profile != null) updateModel(records, profile, timeZone.get())
            }
            .launchIn(scope)
}
