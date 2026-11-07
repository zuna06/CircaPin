package com.circapin.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CircaPinDatabase::class.java,
        listOf(CircaPinDatabase.Migration1To2()),
    )

    @Test
    fun version1To2KeepsProfilesAndSleep() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO user_profile (id, chronotypeScore, sleepNeedHours, targetWakeMinuteOfDay, targetDate, onboardingComplete) " +
                    "VALUES (0, 14, 7.5, 420, 20605, 1)",
            )
            execSQL(
                "INSERT INTO sleep_record (externalId, sleepOnsetMillis, wakeTimeMillis, durationMinutes, isNap, source, qualityScore) " +
                    "VALUES ('hc-1', 1000, 2000, 480, 0, 'HEALTH_CONNECT', 0.9)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(databaseName, 2, true)

        db.query("SELECT chronotypeScore, sleepNeedHours, onboardingComplete FROM user_profile").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(14, cursor.getInt(0))
            assertEquals(7.5f, cursor.getFloat(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.query("SELECT * FROM user_profile").use { cursor ->
            assertFalse("targetWakeMinuteOfDay" in cursor.columnNames)
            assertFalse("targetDate" in cursor.columnNames)
        }
        db.query("SELECT externalId FROM sleep_record").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("hc-1", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM deleted_sync_record").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
