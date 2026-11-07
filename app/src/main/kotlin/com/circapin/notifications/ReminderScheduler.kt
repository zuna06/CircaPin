package com.circapin.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.ReminderPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Singleton
class ReminderScheduler @Inject constructor(private val context: Context) {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    fun schedule(plan: DailyPlan, eatingWindow: EatingWindow?, prefs: ReminderPreferences, wakeTime: Instant) {
        cancelAll()
        if (prefs.lightReminder) scheduleOne(ReminderType.LIGHT, plan.brightLight.start)
        if (prefs.caffeineReminder) scheduleOne(ReminderType.CAFFEINE, plan.caffeineCutoff)
        if (prefs.bedtimeReminder) scheduleOne(ReminderType.BEDTIME, plan.targetBedtime - BEDTIME_LEAD)
        if (prefs.checkInReminder) scheduleOne(ReminderType.CHECK_IN, wakeTime + CHECK_IN_DELAY)
        if (prefs.mealReminder && eatingWindow != null) {
            scheduleOne(ReminderType.MEAL, eatingWindow.lastMeal - MEAL_LEAD)
        }
    }

    fun cancelAll() {
        ReminderType.entries.forEach { type ->
            val intent = pendingIntent(type, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            intent?.let { alarmManager.cancel(it) }
        }
    }

    fun snooze(type: ReminderType) {
        scheduleOne(type, Instant.fromEpochMilliseconds(System.currentTimeMillis()) + SNOOZE_DURATION)
    }

    private fun scheduleOne(type: ReminderType, at: Instant) {
        val millis = at.toEpochMilliseconds()
        if (millis <= System.currentTimeMillis()) return
        val pi = pendingIntent(type, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) ?: return
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun pendingIntent(type: ReminderType, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TYPE, type.name)
        }
        return PendingIntent.getBroadcast(context, type.requestCodeBase, intent, flags)
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        const val ACTION_REMINDER = "com.circapin.REMINDER"
        const val ACTION_SNOOZE = "com.circapin.SNOOZE"
        const val EXTRA_TYPE = "reminder_type"
        val BEDTIME_LEAD = 30.minutes
        val CHECK_IN_DELAY = 15.minutes
        val MEAL_LEAD = 30.minutes
        val SNOOZE_DURATION = 30.minutes
    }
}
