package com.circapin.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.circapin.CircaPinApp
import com.circapin.domain.entities.*
import java.io.FileInputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = ReminderScheduler(context)
    private val now = Clock.System.now()
    private val date = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val plan = DailyPlan(date, 1, TimeWindow(now + 1.hours, now + 2.hours), TimeWindow(now + 3.hours, now + 6.hours), now + 2.hours, now + 6.hours, null)
    private val prefs get() = context.getSharedPreferences("scheduled_reminders", Context.MODE_PRIVATE)
    @Before fun setup() = runBlocking {
        (context.applicationContext as CircaPinApp).stopUpdates()
        scheduler.cancelAll()
    }
    @After fun cleanup() {
        scheduler.cancelAll()
        (context.applicationContext as CircaPinApp).startUpdates()
    }
    @Test fun currentAlarmPermissionUsesTheAppropriateSchedulingPath() {
        val exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        InstrumentationRegistry.getArguments().getString("expectedExact")?.let { assertEquals(it.toBoolean(), exact) }
        scheduler.schedule(plan, null, ReminderPreferences(), now + 14.hours, false)
        assertEquals(exact, prefs.getBoolean("LIGHT0_exact", !exact))
    }
    @Test fun mealTogglesAndCompletedCheckInControlTheirOwnAlarms() {
        val meals = EatingWindow(date, now + 1.hours, now + 4.hours)
        scheduler.schedule(plan, meals, ReminderPreferences(firstMealReminder = true, mealReminder = false), now + 14.hours, true)
        assertTrue(prefs.getLong("FIRST_MEAL0", 0) > 0)
        assertEquals(0, prefs.getLong("MEAL0", -1))
        assertEquals(0, prefs.getLong("CHECK_IN0", -1))
        assertTrue(prefs.getLong("CHECK_IN10", 0) > 0)
        scheduler.snooze(ReminderType.LIGHT)
        scheduler.schedule(plan, meals, ReminderPreferences(firstMealReminder = true), now + 14.hours, true)
        assertEquals(plan.brightLight.start.toEpochMilliseconds(), prefs.getLong("LIGHT0", 0))
    }

    @Test fun receiverPostsAndSnoozesAReminder() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        context.sendBroadcast(android.content.Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER
            putExtra(ReminderScheduler.EXTRA_TYPE, ReminderType.DIM.name)
        })
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (manager.activeNotifications.none { it.id == ReminderType.DIM.requestCodeBase } && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        val posted = requireNotNull(manager.activeNotifications.firstOrNull { it.id == ReminderType.DIM.requestCodeBase })
        assertNotNull(posted.notification.contentIntent)
        assertEquals(context.getString(com.circapin.R.string.reminder_dim_title), posted.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        posted.notification.actions.first().actionIntent.send()
        val snoozeDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (manager.activeNotifications.any { it.id == ReminderType.DIM.requestCodeBase } && android.os.SystemClock.elapsedRealtime() < snoozeDeadline) {
            android.os.SystemClock.sleep(50)
        }
        assertTrue(manager.activeNotifications.none { it.id == ReminderType.DIM.requestCodeBase })
    }
}
