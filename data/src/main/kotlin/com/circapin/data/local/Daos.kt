package com.circapin.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.circapin.domain.entities.GoalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = $SINGLETON_ROW_ID")
    fun observe(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

@Dao
interface SleepRecordDao {
    @Query("SELECT * FROM sleep_record ORDER BY sleepOnsetMillis DESC")
    fun observeAll(): Flow<List<SleepRecordEntity>>

    @Query("SELECT * FROM sleep_record WHERE id = :id")
    suspend fun getById(id: Long): SleepRecordEntity?

    @Query("SELECT id FROM sleep_record WHERE externalId = :externalId")
    suspend fun findIdByExternalId(externalId: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM sleep_record WHERE sleepOnsetMillis < :endMillis AND wakeTimeMillis > :startMillis)")
    suspend fun hasOverlap(startMillis: Long, endMillis: Long): Boolean

    @Query("SELECT * FROM sleep_record WHERE source = 'MANUAL' AND sleepOnsetMillis < :endMillis AND wakeTimeMillis > :startMillis")
    suspend fun getManualOverlapping(startMillis: Long, endMillis: Long): List<SleepRecordEntity>

    @Insert
    suspend fun insert(record: SleepRecordEntity): Long

    @Update
    suspend fun update(record: SleepRecordEntity)

    @Delete
    suspend fun delete(record: SleepRecordEntity)

    @Query("DELETE FROM sleep_record WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Inserts new records and refreshes ones already stored, matching on [SleepRecordEntity.externalId].
     * Manual records the sync replaces are removed in the same transaction, so a night is never lost or doubled.
     */
    @Transaction
    suspend fun saveSynced(records: List<SleepRecordEntity>, supersededManualIds: List<Long>) {
        deleteByIds(supersededManualIds)
        for (record in records) {
            val existingId = record.externalId?.let { findIdByExternalId(it) }
            if (existingId == null) insert(record) else update(record.copy(id = existingId))
        }
    }
}

@Dao
interface ModelStateDao {
    @Query("SELECT * FROM model_state WHERE id = $SINGLETON_ROW_ID")
    fun observe(): Flow<ModelStateEntity?>

    @Upsert
    suspend fun upsert(state: ModelStateEntity)

    @Query("DELETE FROM model_state")
    suspend fun clear()
}

@Dao
interface ModelSnapshotDao {
    @Query("SELECT * FROM model_snapshot ORDER BY capturedAtMillis")
    fun observeAll(): Flow<List<ModelSnapshotEntity>>

    /** The first snapshot at or after [millis], such as a goal's starting phase. */
    @Query("SELECT * FROM model_snapshot WHERE capturedAtMillis >= :millis ORDER BY capturedAtMillis LIMIT 1")
    suspend fun firstSince(millis: Long): ModelSnapshotEntity?

    @Insert
    suspend fun insert(snapshot: ModelSnapshotEntity): Long

    @Delete
    suspend fun delete(snapshot: ModelSnapshotEntity)
}

@Dao
interface ScheduleGoalDao {
    @Query("SELECT * FROM schedule_goal ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<ScheduleGoalEntity>>

    @Query("SELECT * FROM schedule_goal WHERE status = :status ORDER BY createdAtMillis DESC")
    fun observeByStatus(status: GoalStatus): Flow<List<ScheduleGoalEntity>>

    @Insert
    suspend fun insert(goal: ScheduleGoalEntity): Long

    @Update
    suspend fun update(goal: ScheduleGoalEntity)

    @Delete
    suspend fun delete(goal: ScheduleGoalEntity)
}

@Dao
interface DailyPlanDao {
    @Query("SELECT * FROM daily_plan WHERE date = :date")
    fun observe(date: LocalDate): Flow<DailyPlanEntity?>

    @Query("SELECT * FROM daily_plan WHERE goalId = :goalId ORDER BY date")
    suspend fun getForGoal(goalId: Long): List<DailyPlanEntity>

    @Upsert
    suspend fun upsert(plan: DailyPlanEntity)

    @Delete
    suspend fun delete(plan: DailyPlanEntity)
}

@Dao
interface EnergyForecastDao {
    @Query("SELECT * FROM energy_forecast WHERE date = :date")
    fun observe(date: LocalDate): Flow<EnergyForecastEntity?>

    @Upsert
    suspend fun upsert(forecast: EnergyForecastEntity)

    @Delete
    suspend fun delete(forecast: EnergyForecastEntity)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_in ORDER BY date DESC")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_in WHERE date = :date")
    suspend fun get(date: LocalDate): CheckInEntity?

    @Upsert
    suspend fun upsert(checkIn: CheckInEntity)

    @Delete
    suspend fun delete(checkIn: CheckInEntity)
}

@Dao
interface ScreenTimeSnapshotDao {
    @Query("SELECT * FROM screen_time_snapshot WHERE date = :date")
    suspend fun get(date: LocalDate): ScreenTimeSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: ScreenTimeSnapshotEntity)

    @Delete
    suspend fun delete(snapshot: ScreenTimeSnapshotEntity)
}

@Dao
interface SleepScoreDao {
    @Query("SELECT * FROM sleep_score WHERE date >= :from ORDER BY date")
    fun observeSince(from: LocalDate): Flow<List<SleepScoreEntity>>

    @Query("SELECT * FROM sleep_score WHERE date = :date")
    suspend fun get(date: LocalDate): SleepScoreEntity?

    @Upsert
    suspend fun upsert(score: SleepScoreEntity)

    @Delete
    suspend fun delete(score: SleepScoreEntity)
}

@Dao
interface EatingWindowDao {
    @Query("SELECT * FROM eating_window WHERE date = :date")
    fun observe(date: LocalDate): Flow<EatingWindowEntity?>

    @Upsert
    suspend fun upsert(window: EatingWindowEntity)

    @Delete
    suspend fun delete(window: EatingWindowEntity)
}
