package com.circapin

import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.PreferencesRepository
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.GoalCompletionUseCase
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.notifications.PlanReminders
import kotlin.time.Clock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.sync.withLock

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
    private val plans: PlanRepository,
    private val prefs: PreferencesRepository,
    private val checkIns: com.circapin.domain.repository.CheckInRepository,
    private val sleepSync: SleepSync,
    private val refreshToday: RefreshTodayUseCase,
    private val goalCompletion: GoalCompletionUseCase,
    private val reminderScheduler: PlanReminders,
    private val clock: Clock,
    private val timeZone: Provider<TimeZone>,
) {
    fun start(scope: CoroutineScope, tick: Duration = 15.minutes): Job {
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(tick)
            }
        }
        val goalAndSync = combine(goals.observeActive(), sleepSync.lastAttempt, ticks, prefs.observeReminderPreferences(), checkIns.observeAll()) { goal, attempt, _, _, _ -> goal to attempt }
        return combine(sleep.observeRecords(), profiles.observeProfile(), models.observeState(), goalAndSync) {
                records, profile, model, (goal, attempt) ->
            if (profile == null) return@combine
            DataGuard.mutex.withLock {
            val tz = timeZone.get()

            if (goal != null) {
                val date = RefreshTodayUseCase.planDate(clock.now(), tz)
                goalCompletion(goal, date)
            }

            refreshToday(records, profile, model, goal, attempt, tz)

            val date = RefreshTodayUseCase.planDate(clock.now(), tz)
            val plan = plans.getPlan(date)
            if (plan != null) {
                val reminderPrefs = prefs.observeReminderPreferences().first()
                val wakeTime = plan.targetBedtime + profile.sleepNeedHours.toDouble().hours
                val day = plans.observeDay(date).first()
                reminderScheduler.schedule(plan, day?.eatingWindow, reminderPrefs, wakeTime, checkIns.get(date) != null)
            }
            }
        }.launchIn(scope)
    }
}
