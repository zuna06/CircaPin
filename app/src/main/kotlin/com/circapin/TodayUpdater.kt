package com.circapin

import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.RefreshTodayUseCase
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone

/**
 * Keeps today's plan and sleep score current while the app runs. It reacts to new sleep data, model updates,
 * goal changes and sync attempts, and checks again every [tick] so a new day gets its plan without reopening the app.
 */
@Singleton
class TodayUpdater @Inject constructor(
    private val sleep: SleepRepository,
    private val profiles: UserProfileRepository,
    private val models: ModelRepository,
    private val goals: GoalRepository,
    private val sleepSync: SleepSync,
    private val refreshToday: RefreshTodayUseCase,
    private val timeZone: Provider<TimeZone>,
) {
    fun start(scope: CoroutineScope, tick: Duration = 15.minutes): Job {
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(tick)
            }
        }
        val goalAndSync = combine(goals.observeActive(), sleepSync.lastAttempt, ticks) { goal, attempt, _ -> goal to attempt }
        return combine(sleep.observeRecords(), profiles.observeProfile(), models.observeState(), goalAndSync) {
                records, profile, model, (goal, attempt) ->
            if (profile != null) refreshToday(records, profile, model, goal, attempt, timeZone.get())
        }.launchIn(scope)
    }
}
