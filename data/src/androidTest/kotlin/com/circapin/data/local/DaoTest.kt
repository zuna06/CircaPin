package com.circapin.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.MealTimeSource
import com.circapin.domain.entities.SleepSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: CircaPinDatabase
    private val date = LocalDate(2026, 11, 6)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CircaPinDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun sleep(externalId: String? = null, onset: Long = 1_000, wake: Long = 2_000) = SleepRecordEntity(
        externalId = externalId,
        sleepOnsetMillis = onset,
        wakeTimeMillis = wake,
        durationMinutes = 480,
        isNap = false,
        source = SleepSource.MANUAL,
        qualityScore = null,
    )

    private fun goal() = ScheduleGoalEntity(
        label = "Internship",
        targetWakeMinuteOfDay = 420,
        targetDate = date,
        status = GoalStatus.ACTIVE,
        createdAtMillis = 1_000,
    )

    private fun plan(goalId: Long, bedtime: Long = 6) = DailyPlanEntity(
        date = date,
        goalId = goalId,
        lightStartMillis = 1,
        lightEndMillis = 2,
        avoidLightStartMillis = 3,
        avoidLightEndMillis = 4,
        caffeineCutoffMillis = 5,
        targetBedtimeMillis = bedtime,
        napWindowStartMillis = null,
        napWindowEndMillis = null,
        napMaxDurationMinutes = null,
    )

    @Test
    fun userProfileStaysASingleRow() = runTest {
        val dao = db.userProfileDao()
        assertNull(dao.observe().first())

        val profile = UserProfileEntity(
            chronotypeScore = 12,
            sleepNeedHours = 8f,
            onboardingComplete = true,
        )
        dao.upsert(profile)
        dao.upsert(profile.copy(chronotypeScore = 20))
        assertEquals(20, dao.observe().first()?.chronotypeScore)

        dao.clear()
        assertNull(dao.observe().first())
    }

    @Test
    fun sleepRecordInsertUpdateDelete() = runTest {
        val dao = db.sleepRecordDao()
        val id = dao.insert(sleep())

        dao.update(sleep().copy(id = id, isNap = true))
        assertTrue(dao.getById(id)!!.isNap)

        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun sleepRecordsComeBackNewestFirst() = runTest {
        val dao = db.sleepRecordDao()
        dao.insert(sleep(onset = 1_000, wake = 2_000))
        dao.insert(sleep(onset = 5_000, wake = 6_000))

        assertEquals(listOf(5_000L, 1_000L), dao.observeAll().first().map { it.sleepOnsetMillis })
    }

    @Test
    fun overlapChecksTreatTouchingRecordsAsSeparate() = runTest {
        val dao = db.sleepRecordDao()
        dao.insert(sleep(onset = 1_000, wake = 2_000))

        assertTrue(dao.hasOverlap(1_500, 2_500))
        assertTrue(dao.hasOverlap(500, 3_000))
        assertFalse(dao.hasOverlap(2_000, 3_000))
        assertFalse(dao.hasOverlap(0, 1_000))
    }

    @Test
    fun savingSyncedRecordsTwiceDoesNotDuplicateThem() = runTest {
        val dao = db.sleepRecordDao()
        dao.saveSynced(listOf(sleep(externalId = "a"), sleep(externalId = "b", onset = 3_000, wake = 4_000)), emptyList())

        dao.saveSynced(listOf(sleep(externalId = "a", wake = 2_500)), emptyList())

        val records = dao.observeAll().first()
        assertEquals(2, records.size)
        assertEquals(2_500L, records.single { it.externalId == "a" }.wakeTimeMillis)
    }

    @Test
    fun savingSyncedRecordsRemovesTheManualRecordsTheyReplace() = runTest {
        val dao = db.sleepRecordDao()
        val replaced = dao.insert(sleep())
        val kept = dao.insert(sleep(onset = 5_000, wake = 6_000))

        dao.saveSynced(listOf(sleep(externalId = "a", onset = 1_100, wake = 2_100)), listOf(replaced))

        val ids = dao.observeAll().first().map { it.id }
        assertEquals(2, ids.size)
        assertTrue(kept in ids)
        assertFalse(replaced in ids)
    }

    @Test
    fun aDeletedSyncedRecordIsNotSyncedBackIn() = runTest {
        val dao = db.sleepRecordDao()
        dao.saveSynced(listOf(sleep(externalId = "a"), sleep(externalId = "b", onset = 3_000, wake = 4_000)), emptyList())

        dao.deleteAndRemember(dao.observeAll().first().single { it.externalId == "a" }, deletedAtMillis = 9_000)
        dao.saveSynced(listOf(sleep(externalId = "a"), sleep(externalId = "b", onset = 3_000, wake = 4_000)), emptyList())

        assertEquals(listOf("b"), dao.observeAll().first().map { it.externalId })
        assertEquals(listOf("a"), dao.getDeletedExternalIds())
    }

    @Test
    fun deletingAManualRecordLeavesNoTombstone() = runTest {
        val dao = db.sleepRecordDao()
        val id = dao.insert(sleep())

        dao.deleteAndRemember(dao.getById(id)!!, deletedAtMillis = 9_000)

        assertNull(dao.getById(id))
        assertTrue(dao.getDeletedExternalIds().isEmpty())
    }

    @Test
    fun modelHistoryOnlyGrowsWhenTheEstimateChanges() = runTest {
        val dao = db.modelStateDao()
        val state = ModelStateEntity(
            estimatedDlmoMillis = 1_000,
            sleepDebtHours = 2f,
            socialJetlagHours = 1f,
            confidence = 0.5f,
            lastUpdatedMillis = 10,
        )

        dao.save(state)
        dao.save(state.copy(lastUpdatedMillis = 20))
        dao.save(state.copy(sleepDebtHours = 3f, lastUpdatedMillis = 30))

        assertEquals(3f, dao.observe().first()?.sleepDebtHours)
        assertEquals(listOf(10L, 30L), db.modelSnapshotDao().observeAll().first().map { it.capturedAtMillis })
    }

    @Test
    fun onlyManualRecordsCountAsManualOverlaps() = runTest {
        val dao = db.sleepRecordDao()
        val manual = dao.insert(sleep(onset = 1_000, wake = 2_000))
        dao.insert(sleep(externalId = "a", onset = 1_000, wake = 2_000).copy(source = SleepSource.HEALTH_CONNECT))
        dao.insert(sleep(onset = 3_000, wake = 4_000))

        assertEquals(listOf(manual), dao.getManualOverlapping(500, 2_500).map { it.id })
    }

    @Test
    fun manualRecordsWithoutExternalIdsCanCoexist() = runTest {
        val dao = db.sleepRecordDao()
        dao.insert(sleep())
        dao.insert(sleep(onset = 3_000, wake = 4_000))

        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun modelStateStaysASingleRow() = runTest {
        val dao = db.modelStateDao()
        val state = ModelStateEntity(
            estimatedDlmoMillis = 1,
            sleepDebtHours = 2f,
            socialJetlagHours = 3f,
            confidence = 0.5f,
            lastUpdatedMillis = 4,
        )
        dao.upsert(state)
        dao.upsert(state.copy(sleepDebtHours = 7f))
        assertEquals(7f, dao.observe().first()?.sleepDebtHours)

        dao.clear()
        assertNull(dao.observe().first())
    }

    @Test
    fun modelSnapshotsKeepHistory() = runTest {
        val dao = db.modelSnapshotDao()
        fun snapshot(at: Long) = ModelSnapshotEntity(
            capturedAtMillis = at,
            estimatedDlmoMillis = at,
            sleepDebtHours = 0f,
            socialJetlagHours = 0f,
            confidence = 0f,
        )
        dao.insert(snapshot(100))
        dao.insert(snapshot(300))
        dao.insert(snapshot(200))

        assertEquals(3, dao.observeAll().first().size)
        val start = dao.firstSince(150)!!
        assertEquals(200L, start.capturedAtMillis)

        dao.delete(start)
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun scheduleGoalInsertUpdateDelete() = runTest {
        val dao = db.scheduleGoalDao()
        val id = dao.insert(goal())
        assertEquals(date, dao.observeAll().first().single().targetDate)

        dao.update(goal().copy(id = id, status = GoalStatus.COMPLETED))
        assertTrue(dao.observeByStatus(GoalStatus.ACTIVE).first().isEmpty())
        assertEquals(id, dao.observeByStatus(GoalStatus.COMPLETED).first().single().id)

        dao.delete(goal().copy(id = id))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun dailyPlanBelongsToAGoal() = runTest {
        val dao = db.dailyPlanDao()
        val goalId = db.scheduleGoalDao().insert(goal())

        dao.upsert(plan(goalId))
        dao.upsert(plan(goalId, bedtime = 60))

        assertEquals(60L, dao.observe(date).first()?.targetBedtimeMillis)
        assertEquals(1, dao.getForGoal(goalId).size)

        dao.delete(plan(goalId))
        assertNull(dao.observe(date).first())
    }

    @Test
    fun dailyPlanRejectsAnUnknownGoal() = runTest {
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { db.dailyPlanDao().upsert(plan(goalId = 99)) }
        }
    }

    @Test
    fun deletingAGoalRemovesItsPlans() = runTest {
        val goalId = db.scheduleGoalDao().insert(goal())
        db.dailyPlanDao().upsert(plan(goalId))

        db.scheduleGoalDao().delete(goal().copy(id = goalId))

        assertNull(db.dailyPlanDao().observe(date).first())
    }

    @Test
    fun energyForecastKeepsAll24Hours() = runTest {
        val dao = db.energyForecastDao()
        val curve = List(24) { it / 24f }
        dao.upsert(EnergyForecastEntity(date, curve))
        assertEquals(curve, dao.observe(date).first()?.hourlyAlertness)

        dao.upsert(EnergyForecastEntity(date, List(24) { 1f }))
        assertEquals(1f, dao.observe(date).first()?.hourlyAlertness?.first())

        dao.delete(EnergyForecastEntity(date, emptyList()))
        assertNull(dao.observe(date).first())
    }

    @Test
    fun checkInUpsertAndDelete() = runTest {
        val dao = db.checkInDao()
        val checkIn = CheckInEntity(date, 3, 0.6f, -0.1f, 15)
        dao.upsert(checkIn)
        dao.upsert(checkIn.copy(reportedAlertness = 4))
        assertEquals(4, dao.get(date)?.reportedAlertness)
        assertEquals(1, dao.observeAll().first().size)

        dao.delete(checkIn)
        assertNull(dao.get(date))
    }

    @Test
    fun screenTimeSnapshotUpsertAndDelete() = runTest {
        val dao = db.screenTimeSnapshotDao()
        val snapshot = ScreenTimeSnapshotEntity(date, 120, 30)
        dao.upsert(snapshot)
        dao.upsert(snapshot.copy(afterDimmingMinutes = 45))
        assertEquals(45, dao.get(date)?.afterDimmingMinutes)

        dao.delete(snapshot)
        assertNull(dao.get(date))
    }

    @Test
    fun sleepScoreUpsertAndDelete() = runTest {
        val dao = db.sleepScoreDao()
        val score = SleepScoreEntity(date, 75, 80, 70, 75, 75, "Good")
        dao.upsert(score)
        dao.upsert(score.copy(overallScore = 80))
        assertEquals(80, dao.get(date)?.overallScore)
        assertEquals(1, dao.observeSince(date).first().size)

        dao.delete(score)
        assertNull(dao.get(date))
    }

    @Test
    fun eatingWindowUpsertAndDelete() = runTest {
        val dao = db.eatingWindowDao()
        val window = EatingWindowEntity(date, 1, 2, MealTimeSource.PLAN)
        dao.upsert(window)
        dao.upsert(window.copy(source = MealTimeSource.MANUAL_OVERRIDE))
        assertEquals(MealTimeSource.MANUAL_OVERRIDE, dao.observe(date).first()?.source)

        dao.delete(window)
        assertNull(dao.observe(date).first())
    }
}
