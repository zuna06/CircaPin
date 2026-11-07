package com.circapin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserProfileEntity::class,
        SleepRecordEntity::class,
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
    version = 1,
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

    companion object {
        private const val FILE_NAME = "circapin.db"

        fun create(context: Context): CircaPinDatabase =
            Room.databaseBuilder(context, CircaPinDatabase::class.java, FILE_NAME).build()
    }
}
