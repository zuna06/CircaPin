package com.circapin.worker

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import com.circapin.CircaPinApp
import com.circapin.data.local.CircaPinDatabase
import com.circapin.data.local.UserProfileEntity
import com.circapin.notifications.ReminderRestoreReceiver
import com.circapin.notifications.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real persistent WorkManager, Hilt worker and Room; only the initial wait is accelerated. */
@RunWith(AndroidJUnit4::class)
class DailyPlanChainTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = context.applicationContext as CircaPinApp
    private val work get() = WorkManager.getInstance(context)
    private lateinit var db: CircaPinDatabase
    private val originalZone = java.util.TimeZone.getDefault()
    private val name = "circapin_daily_rollover"

    @Before fun setup() = runBlocking {
        app.stopUpdates()
        withContext(Dispatchers.IO) {
            work.cancelAllWork().result.get()
            db = CircaPinDatabase.create(context)
            db.clearAllTables()
            db.userProfileDao().upsert(UserProfileEntity(chronotypeScore = 14, sleepNeedHours = 8f, onboardingComplete = true))
        }
    }

    @After fun cleanup() = runBlocking {
        java.util.TimeZone.setDefault(originalZone)
        withContext(Dispatchers.IO) {
            work.cancelAllWork().result.get()
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_model_write")
            db.clearAllTables()
            db.close()
        }
        ReminderScheduler(context).cancelAll()
        app.startUpdates()
    }

    private suspend fun infos() = withContext(Dispatchers.IO) { work.getWorkInfosForUniqueWork(name).get() }
    private suspend fun info(id: UUID) = withContext(Dispatchers.IO) { requireNotNull(work.getWorkInfoById(id).get()) }
    private suspend fun awaitCondition(condition: suspend () -> Boolean) = withTimeout(60_000) {
        while (!condition()) delay(100)
    }
    private suspend fun scheduled(): WorkInfo {
        awaitCondition { infos().count { !it.state.isFinished } == 1 }
        return infos().single { !it.state.isFinished }
    }
    private suspend fun runNow(id: UUID) = withContext(Dispatchers.IO) {
        // Preserve the production ID/unique chain and rollover input. No test-only worker.
        work.updateWork(OneTimeWorkRequestBuilder<DailyPlanWorker>().setId(id)
            .setInputData(workDataOf("rollover" to true))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build()).get()
    }
    private fun assertNextBoundary(info: WorkInfo, zone: ZoneId = ZoneId.systemDefault()) {
        val expected = DailyRollover.next(Instant.now(), zone).toEpochMilli()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue("Expected next local 4 AM, got ${info.nextScheduleTimeMillis}",
            kotlin.math.abs(expected - info.nextScheduleTimeMillis) < 5_000)
    }

    @Test fun successfulRunCreatesExactlyOneDelayedSuccessor() = runBlocking {
        DailyPlanWorker.enqueue(context)
        val first = scheduled()
        assertNextBoundary(first)
        DailyPlanWorker.enqueue(context) // startup is idempotent
        delay(200)
        assertEquals(first.id, scheduled().id)
        runNow(first.id)
        awaitCondition { info(first.id).state == WorkInfo.State.SUCCEEDED }
        val successor = scheduled()
        assertNotEquals(first.id, successor.id)
        assertNextBoundary(successor)
    }

    @Test fun transientFailureRetriesSameWorkWithoutCreatingAnExtraSuccessor() = runBlocking {
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_model_write BEFORE INSERT ON model_state BEGIN SELECT RAISE(ABORT, 'temporary model failure'); END")
        }
        DailyPlanWorker.enqueue(context)
        val first = scheduled()
        runNow(first.id)
        awaitCondition { info(first.id).let { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } }
        assertEquals(listOf(first.id), infos().filter { !it.state.isFinished }.map { it.id })
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_model_write") }
        awaitCondition { info(first.id).state == WorkInfo.State.SUCCEEDED }
        val successor = scheduled()
        assertNotEquals(first.id, successor.id)
        assertNextBoundary(successor)
    }

    @Test fun clockAndTimeZoneRestoreReplacePendingChain() = runBlocking {
        DailyPlanWorker.enqueue(context)
        var previous = scheduled()
        for ((action, zone) in listOf(Intent.ACTION_TIME_CHANGED to "America/New_York",
            Intent.ACTION_TIMEZONE_CHANGED to "America/Los_Angeles")) {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
            ReminderRestoreReceiver().apply { scheduler = ReminderScheduler(context) }
                .onReceive(context, Intent(action))
            // REPLACE may remove the cancelled record immediately from WorkManager's database.
            awaitCondition {
                withContext(Dispatchers.IO) { work.getWorkInfoById(previous.id).get() }
                    .let { it == null || it.state == WorkInfo.State.CANCELLED }
            }
            val replacement = scheduled()
            assertNotEquals(previous.id, replacement.id)
            assertNextBoundary(replacement, ZoneId.of(zone))
            previous = replacement
        }
    }
}
