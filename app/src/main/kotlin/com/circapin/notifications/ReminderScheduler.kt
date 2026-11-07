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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Singleton
class ReminderScheduler @Inject constructor(private val context: Context) : PlanReminders {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private val scheduled by lazy { context.getSharedPreferences("scheduled_reminders", Context.MODE_PRIVATE) }

    override fun schedule(plan: DailyPlan, eatingWindow: EatingWindow?, prefs: ReminderPreferences, wakeTime: Instant, checkedIn: Boolean) {
        update(ReminderType.LIGHT, plan.brightLight.start.takeIf { prefs.lightReminder })
        update(ReminderType.CAFFEINE, plan.caffeineCutoff.takeIf { prefs.caffeineReminder })
        update(ReminderType.BEDTIME, (plan.targetBedtime - BEDTIME_LEAD).takeIf { prefs.bedtimeReminder })
        update(ReminderType.DIM, plan.avoidLight.start.takeIf { prefs.dimReminder })
        update(ReminderType.MEAL, eatingWindow?.lastMeal?.minus(MEAL_LEAD)?.takeIf { prefs.mealReminder })
        update(ReminderType.FIRST_MEAL, eatingWindow?.firstMeal?.takeIf { prefs.firstMealReminder })
        val tz = TimeZone.currentSystemDefault()
        val wakeClock = (wakeTime + CHECK_IN_DELAY).toLocalDateTime(tz).time
        update(ReminderType.CHECK_IN, LocalDateTime(plan.date, wakeClock).toInstant(tz).takeIf { prefs.checkInReminder && !checkedIn })
        val next = LocalDate.fromEpochDays(plan.date.toEpochDays() + 1)
        update(ReminderType.CHECK_IN, LocalDateTime(next, wakeClock).toInstant(tz).takeIf { prefs.checkInReminder }, 10)
    }

    private fun update(type: ReminderType, at: Instant?, slot: Int = 0) {
        val key = "${type.name}$slot"
        val target = at?.toEpochMilliseconds() ?: 0L
        if (at == null) androidx.core.app.NotificationManagerCompat.from(context).cancel(type.requestCodeBase)
        val exact = canScheduleExact()
        if (scheduled.getLong(key, -1) == target && scheduled.getBoolean("${key}_exact", !exact) == exact) return
        pendingIntent(type, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE, slot)?.let { alarmManager.cancel(it) }
        scheduled.edit().putLong(key, target).putBoolean("${key}_exact", exact).apply()
        if (at != null) scheduleOne(type, at, slot)
    }

    override fun cancelCheckInToday() {
        pendingIntent(ReminderType.CHECK_IN, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarmManager.cancel(it) }
    }

    fun cancelAll() {
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
        ReminderType.entries.forEach { type ->
            val intent = pendingIntent(type, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            intent?.let { alarmManager.cancel(it) }
        }
        pendingIntent(ReminderType.CHECK_IN, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE, 10)?.let { alarmManager.cancel(it) }
        scheduled.edit().clear().apply()
    }

    fun snooze(type: ReminderType) {
        scheduleOne(type, Instant.fromEpochMilliseconds(System.currentTimeMillis()) + SNOOZE_DURATION)
    }

    private fun scheduleOne(type: ReminderType, at: Instant, slot: Int = 0) {
        val millis = at.toEpochMilliseconds()
        if (millis <= System.currentTimeMillis()) return
        val pi = pendingIntent(type, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, slot) ?: return
        try { if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } } catch (_: SecurityException) { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    }

    private fun pendingIntent(type: ReminderType, flags: Int, slot: Int = 0): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TYPE, type.name)
        }
        return PendingIntent.getBroadcast(context, type.requestCodeBase + slot, intent, flags)
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
