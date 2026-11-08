package com.circapin.worker

import android.content.Context
import com.circapin.DataGuard
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.circapin.domain.repository.GoalRepository
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.PreferencesRepository
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.GoalCompletionUseCase
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.domain.usecases.UpdateModelUseCase
import com.circapin.notifications.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Refreshes the plan near local 4 AM; Health Connect sync needs background read access.
 * Syncs sleep from Health Connect, recomputes the model, and refreshes today's plan.
 */
@HiltWorker
class DailyPlanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sleep: SleepRepository,
    private val profiles: UserProfileRepository,
    private val models: ModelRepository,
    private val goals: GoalRepository,
    private val plans: PlanRepository,
    private val prefs: PreferencesRepository,
    private val updateModel: UpdateModelUseCase,
    private val refreshToday: RefreshTodayUseCase,
    private val goalCompletion: GoalCompletionUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val permissions: com.circapin.ui.settings.DevicePermissions,
    private val checkIns: com.circapin.domain.repository.CheckInRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        refresh()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    private suspend fun refresh(): Result = DataGuard.mutex.withLock {
        val profile = profiles.observeProfile().first()
        if (profile == null) {
            if (inputData.getBoolean(ROLLOVER, false) && !isStopped) enqueue(applicationContext, append = true)
            return@withLock Result.success()
        }
        val timeZone = TimeZone.currentSystemDefault()
        try { if (permissions.read().background) sleep.syncFromHealthConnect() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* fall back to stored data */ }

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val records = sleep.observeRecords().first()

        updateModel(records, profile, timeZone)

        val model = models.observeState().first()
        val goal = goals.observeActive().first()

        if (goal != null) {
            val date = RefreshTodayUseCase.planDate(now, timeZone)
            goalCompletion(goal, date)
        }

        refreshToday(records, profile, model, goal, now, timeZone)

        val date = RefreshTodayUseCase.planDate(now, timeZone)
        val plan = plans.getPlan(date)
        if (plan != null) {
            val reminderPrefs = prefs.observeReminderPreferences().first()
            val nextWakeTime = plan.targetBedtime + profile.sleepNeedHours.toDouble().hours
            val day = plans.observeDay(date).first()
            reminderScheduler.schedule(plan, day?.eatingWindow, reminderPrefs, nextWakeTime, checkIns.get(date) != null)
        }

        // Keep successor creation serialized with data reset. Ad-hoc restores do not fork the chain.
        if (inputData.getBoolean(ROLLOVER, false) && !isStopped) enqueue(applicationContext, append = true)
        Result.success()
    }

    companion object {
        private const val ROLLOVER = "rollover"

        fun enqueue(context: Context, append: Boolean = false, realign: Boolean = false) {
            val manager = WorkManager.getInstance(context)
            // Retire the previous fixed-24-hour periodic schedule on upgrade.
            manager.cancelUniqueWork("circapin_daily_plan")
            val now = java.time.Instant.now()
            val next = DailyRollover.next(now, java.time.ZoneId.systemDefault())
            val request = OneTimeWorkRequestBuilder<DailyPlanWorker>()
                .setInputData(workDataOf(ROLLOVER to true))
                .setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            val policy = when {
                realign -> ExistingWorkPolicy.REPLACE
                append -> ExistingWorkPolicy.APPEND_OR_REPLACE
                else -> ExistingWorkPolicy.KEEP
            }
            manager.enqueueUniqueWork("circapin_daily_rollover", policy, request)
        }
    }
}
