package com.circapin.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [
        UserProfileEntity::class,
        SleepRecordEntity::class,
        DeletedSyncRecordEntity::class,
        ModelStateEntity::class,
        ModelSnapshotEntity::class,
        ScheduleGoalEntity::class,
        DailyPlanEntity::class,
        EnergyForecastEntity::class,
        CheckInEntity::class,
        ScreenTimeSnapshotEntity::class,
        SleepScoreEntity::class,
        EatingWindowEntity::class,
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = CircaPinDatabase.Migration1To2::class),
    ],
)
@TypeConverters(Converters::class)
abstract class CircaPinDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun modelStateDao(): ModelStateDao
    abstract fun modelSnapshotDao(): ModelSnapshotDao
    abstract fun scheduleGoalDao(): ScheduleGoalDao
    abstract fun dailyPlanDao(): DailyPlanDao
    abstract fun energyForecastDao(): EnergyForecastDao
    abstract fun checkInDao(): CheckInDao
    abstract fun screenTimeSnapshotDao(): ScreenTimeSnapshotDao
    abstract fun sleepScoreDao(): SleepScoreDao
    abstract fun eatingWindowDao(): EatingWindowDao

    /** Moves schedule targets out of the profile (they live in schedule_goal) and adds deleted_sync_record. */
    @DeleteColumn(tableName = "user_profile", columnName = "targetWakeMinuteOfDay")
    @DeleteColumn(tableName = "user_profile", columnName = "targetDate")
    class Migration1To2 : AutoMigrationSpec

    companion object {
        private const val FILE_NAME = "circapin.db"

        fun create(context: Context): CircaPinDatabase =
            Room.databaseBuilder(context, CircaPinDatabase::class.java, FILE_NAME).build()
    }
}
