package com.circapin.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.data.local.*
import com.circapin.domain.entities.*
import com.circapin.notifications.ReminderScheduler
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataControlsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: CircaPinDatabase
    private lateinit var controls: DataControls
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, CircaPinDatabase::class.java).build()
        controls = DataControls(database, ReminderScheduler(context), context)
    }
    @After fun close() { database.close() }

    @Test fun jsonExportContainsEveryTableNullsAndSchemaVersion() = runBlocking {
        database.sleepRecordDao().insert(SleepRecordEntity(externalId = null, sleepOnsetMillis = 1000, wakeTimeMillis = 2000,
            durationMinutes = 0, isNap = false, source = SleepSource.MANUAL, qualityScore = null))
        val export = controls.export(false)
        val root = JSONObject(export.readText())
        assertEquals(1, root.getInt("exportVersion"))
        assertEquals(2, root.getInt("databaseVersion"))
        DataControls.TABLES.forEach { assertTrue(root.has(it)) }
        val record = root.getJSONArray("sleep_record").getJSONObject(0)
        assertTrue(record.isNull("qualityScore"))
        assertEquals(1000, record.getLong("sleepOnsetMillis"))
    }
    @Test fun csvArchiveContainsHeadersAndEscapesQuotedMultilineLabels() = runBlocking {
        val label = "Finals, \"early\"\nMonday"
        database.sleepRecordDao().insert(SleepRecordEntity(externalId = null, sleepOnsetMillis = 1000, wakeTimeMillis = 2000,
            durationMinutes = 0, isNap = false, source = SleepSource.MANUAL, qualityScore = null))
        database.scheduleGoalDao().insert(ScheduleGoalEntity(label = label, targetWakeMinuteOfDay = 420,
            targetDate = LocalDate(2026, 11, 10), status = GoalStatus.ACTIVE, createdAtMillis = 1000))
        ZipFile(controls.export(true)).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            DataControls.TABLES.forEach { assertNotNull(zip.getEntry("$it.csv")) }
            val csv = zip.getInputStream(zip.getEntry("schedule_goal.csv")).bufferedReader().readText()
            assertTrue(csv.contains("\"targetWakeMinuteOfDay\""))
            assertTrue(csv.contains("\"Finals, \"\"early\"\"\nMonday\""))
            val sleepCsv = zip.getInputStream(zip.getEntry("sleep_record.csv")).bufferedReader().readText()
            assertTrue(sleepCsv.contains("\\N"))
        }
    }
    @Test fun deleteAllRemovesAllTablesPreferencesAndPrivateExports() = runBlocking {
        database.userProfileDao().upsert(UserProfileEntity(chronotypeScore = 14, sleepNeedHours = 8f, onboardingComplete = true))
        database.checkInDao().upsert(CheckInEntity(LocalDate(2026, 11, 10), 3, 0.5f, 0f, 0))
        val export = controls.export(false)
        context.getSharedPreferences("circapin_prefs", Context.MODE_PRIVATE).edit().putBoolean("reminder_light", false).commit()
        controls.deleteAll()
        DataControls.TABLES.forEach { table ->
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        }
        assertTrue(context.getSharedPreferences("circapin_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertFalse(export.exists())
    }
}
