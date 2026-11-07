package com.circapin.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.circapin.DataGuard
import com.circapin.data.local.CircaPinDatabase
import com.circapin.data.local.ScreenTimeSnapshotEntity
import com.circapin.domain.model.ScreenTransition
import com.circapin.domain.model.ScreenUsage
import com.circapin.domain.repository.PlanRepository
import com.circapin.domain.repository.PreferencesRepository
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.notifications.ReminderScheduler
import com.circapin.notifications.ReminderReceiver
import com.circapin.notifications.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Singleton
class ScreenTimeTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CircaPinDatabase,
    private val plans: PlanRepository,
    private val preferences: PreferencesRepository,
    private val profiles: UserProfileRepository,
    private val clock: Clock,
) {
    val snapshot = MutableStateFlow<ScreenTimeSnapshotEntity?>(null)

    @Suppress("DEPRECATION")
    fun hasPermission(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    suspend fun refresh() = withContext(Dispatchers.IO) {
        DataGuard.mutex.withLock {
            if (profiles.observeProfile().first() == null || !hasPermission()) {
                snapshot.value = null
                return@withLock
            }
            val now = clock.now()
            val tz = TimeZone.currentSystemDefault()
            val date = now.toLocalDateTime(tz).date
            val start = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz)
            val planDate = RefreshTodayUseCase.planDate(now, tz)
            val plan = plans.getPlan(planDate)
            val events = context.getSystemService(UsageStatsManager::class.java)
                .queryEvents((start - 1.days).toEpochMilliseconds(), now.toEpochMilliseconds()) ?: return@withLock
            val transitions = mutableListOf<ScreenTransition>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.KEYGUARD_HIDDEN -> transitions += ScreenTransition(event.timeStamp, true)
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> transitions += ScreenTransition(event.timeStamp, false)
                }
            }
            val total = ScreenUsage.minutes(transitions, start.toEpochMilliseconds(), now.toEpochMilliseconds())
            val after = plan?.let { ScreenUsage.minutes(transitions, it.avoidLight.start.toEpochMilliseconds(), now.toEpochMilliseconds()) } ?: 0
            val result = ScreenTimeSnapshotEntity(date, total, after)
            database.screenTimeSnapshotDao().upsert(result)
            snapshot.value = result
            val sent = context.getSharedPreferences("screen_nudge", Context.MODE_PRIVATE)
            if (plan != null && now < plan.targetBedtime && after >= 30 &&
                preferences.observeReminderPreferences().first().bedtimeReminder && sent.getString("date", null) != planDate.toString()) {
                context.sendBroadcast(Intent(context, ReminderReceiver::class.java).apply {
                    action = ReminderScheduler.ACTION_REMINDER
                    putExtra(ReminderScheduler.EXTRA_TYPE, ReminderType.BEDTIME.name)
                })
                sent.edit().putString("date", planDate.toString()).apply()
            }
        }
    }
}
