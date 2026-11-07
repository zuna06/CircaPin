package com.circapin.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
 * Runs once a day in the background (if READ_HEALTH_DATA_IN_BACKGROUND is granted).
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val timeZone = TimeZone.currentSystemDefault()
        try { sleep.syncFromHealthConnect() } catch (_: Exception) { /* fall back to stored data */ }

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val records = sleep.observeRecords().first()
        val profile = profiles.observeProfile().first() ?: return Result.success()

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
            val wakeTime = plan.targetBedtime + profile.sleepNeedHours.toDouble().hours
            val day = plans.observeDay(date).first()
            reminderScheduler.schedule(plan, day?.eatingWindow, reminderPrefs, wakeTime)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "circapin_daily_plan"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyPlanWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(millisUntilFourAm(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun millisUntilFourAm(): Long {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 4)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
