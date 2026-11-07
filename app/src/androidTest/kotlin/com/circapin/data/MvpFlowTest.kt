package com.circapin.data

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import com.circapin.CircaPinApp
import com.circapin.MainActivity
import com.circapin.data.local.*
import com.circapin.domain.entities.*
import com.circapin.domain.usecases.RefreshTodayUseCase
import com.circapin.worker.DailyPlanWorker
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Hilt worker and the actual reset/navigation path on disposable emulator data. */
@RunWith(AndroidJUnit4::class)
class MvpFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun backgroundPlanningAndResetWorkThroughTheAppDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as CircaPinApp
        val work = WorkManager.getInstance(context)
        app.stopUpdates()
        withContext(Dispatchers.IO) { work.cancelAllWork().result.get() }
        val database = CircaPinDatabase.create(context)
        val now = Clock.System.now()
        val date = RefreshTodayUseCase.planDate(now, TimeZone.currentSystemDefault())
        try {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                database.userProfileDao().upsert(UserProfileEntity(chronotypeScore = 14, sleepNeedHours = 8f, onboardingComplete = true))
                database.scheduleGoalDao().replaceActive(ScheduleGoalEntity(label = null, targetWakeMinuteOfDay = 420,
                    targetDate = LocalDate.fromEpochDays(date.toEpochDays() + 14), status = GoalStatus.ACTIVE, createdAtMillis = now.toEpochMilliseconds()))
                database.sleepRecordDao().insert(SleepRecordEntity(externalId = null, sleepOnsetMillis = (now - 10.hours).toEpochMilliseconds(),
                    wakeTimeMillis = (now - 2.hours).toEpochMilliseconds(), durationMinutes = 480, isNap = false, source = SleepSource.MANUAL, qualityScore = null))
            }
            val request = OneTimeWorkRequestBuilder<DailyPlanWorker>().build()
            work.enqueue(request)
            val result = withTimeout(30_000) {
                while (true) {
                    val info = withContext(Dispatchers.IO) { work.getWorkInfoById(request.id).get() }
                    if (info?.state?.isFinished == true) return@withTimeout info.state
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") WorkInfo.State.FAILED
            }
            assertEquals(WorkInfo.State.SUCCEEDED, result)
            assertNotNull(database.dailyPlanDao().get(date))
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Settings").performClick()
                compose.onNodeWithText("Delete all data").performScrollTo().performClick()
                compose.onAllNodesWithText("Delete all data")[2].performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
                }
                withContext(Dispatchers.IO) {
                    database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM user_profile").use { cursor ->
                        cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                    }
                }
            }
        } finally { database.close(); app.startUpdates() }
    }
}
