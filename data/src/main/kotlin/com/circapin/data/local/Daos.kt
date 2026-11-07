package com.circapin.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberDeleted(deleted: DeletedSyncRecordEntity)

    @Query("SELECT externalId FROM deleted_sync_record")
    suspend fun getDeletedExternalIds(): List<String>

    /** Deletes a record. A synced one is also remembered, so the next sync skips it. */
    @Transaction
    suspend fun deleteAndRemember(record: SleepRecordEntity, deletedAtMillis: Long) {
        delete(record)
        record.externalId?.let { rememberDeleted(DeletedSyncRecordEntity(it, deletedAtMillis)) }
    }

    /**
     * Inserts new records and refreshes ones already stored, matching on [SleepRecordEntity.externalId].
     * Manual records the sync replaces are removed in the same transaction, so a night is never lost or doubled.
     * Records the user deleted are skipped.
     */
    @Transaction
    suspend fun saveSynced(records: List<SleepRecordEntity>, supersededManualIds: List<Long>) {
        deleteByIds(supersededManualIds)
        val deleted = getDeletedExternalIds().toSet()
        for (record in records) {
            if (record.externalId in deleted) continue
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

    @Query("SELECT * FROM model_snapshot ORDER BY capturedAtMillis DESC, id DESC LIMIT 1")
    suspend fun latestSnapshot(): ModelSnapshotEntity?

    @Insert
    suspend fun insertSnapshot(snapshot: ModelSnapshotEntity): Long

    /**
     * Stores the current estimate, and adds it to the history only when it differs from the last snapshot.
     * The app recomputes on every launch, so this keeps the history to real changes.
     */
    @Transaction
    suspend fun save(state: ModelStateEntity) {
        upsert(state)
        val snapshot = ModelSnapshotEntity(
            capturedAtMillis = state.lastUpdatedMillis,
            estimatedDlmoMillis = state.estimatedDlmoMillis,
            sleepDebtHours = state.sleepDebtHours,
            socialJetlagHours = state.socialJetlagHours,
            confidence = state.confidence,
        )
        val latest = latestSnapshot()
        if (latest == null || latest.copy(id = 0, capturedAtMillis = snapshot.capturedAtMillis) != snapshot) {
            insertSnapshot(snapshot)
        }
    }
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

    @Query("SELECT * FROM schedule_goal WHERE status IN ('ACTIVE', 'COMPLETED', 'MAINTAINED') ORDER BY createdAtMillis DESC, id DESC LIMIT 1")
    fun observeActive(): Flow<ScheduleGoalEntity?>

    @Query("UPDATE schedule_goal SET status = :to WHERE status = :from")
    suspend fun updateStatus(from: GoalStatus, to: GoalStatus)

    /** Abandons any active goal and inserts [goal] as the new active one, so only one is ever active. */
    @Transaction
    suspend fun replaceActive(goal: ScheduleGoalEntity): Long {
        updateStatus(GoalStatus.ACTIVE, GoalStatus.ABANDONED)
        return insert(goal.copy(id = 0, status = GoalStatus.ACTIVE))
    }

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

    @Query("SELECT * FROM daily_plan WHERE date = :date")
    suspend fun get(date: LocalDate): DailyPlanEntity?

    @Query("SELECT * FROM daily_plan WHERE goalId = :goalId ORDER BY date")
    suspend fun getForGoal(goalId: Long): List<DailyPlanEntity>

    @Upsert
    suspend fun upsert(plan: DailyPlanEntity)

    @Upsert
    suspend fun upsertForecast(forecast: EnergyForecastEntity)

    @Upsert
    suspend fun upsertEatingWindow(window: EatingWindowEntity)

    /** Stores a day's plan with its forecast and eating window, so the home screen never sees half a day. */
    @Transaction
    suspend fun saveDay(plan: DailyPlanEntity, eatingWindow: EatingWindowEntity, forecast: EnergyForecastEntity) {
        upsert(plan)
        upsertEatingWindow(eatingWindow)
        upsertForecast(forecast)
    }

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

    @Query("SELECT * FROM sleep_score WHERE date = :date")
    fun observe(date: LocalDate): Flow<SleepScoreEntity?>

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
