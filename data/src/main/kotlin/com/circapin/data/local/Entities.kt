package com.circapin.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.MealTimeSource
import com.circapin.domain.entities.SleepSource
import kotlinx.datetime.LocalDate

/** Primary key of the tables that only ever hold one row. */
internal const val SINGLETON_ROW_ID = 0

/** Schedule targets live only in [ScheduleGoalEntity], so a profile never disagrees with the active goal. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ROW_ID,
    val chronotypeScore: Int,
    val sleepNeedHours: Float,
    val onboardingComplete: Boolean,
)

/** Health Connect records the user deleted, so the next sync doesn't bring them back. */
@Entity(tableName = "deleted_sync_record")
data class DeletedSyncRecordEntity(
    @PrimaryKey val externalId: String,
    val deletedAtMillis: Long,
)

@Entity(
    tableName = "sleep_record",
    indices = [Index(value = ["externalId"], unique = true), Index("sleepOnsetMillis")],
)
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String?,
    val sleepOnsetMillis: Long,
    val wakeTimeMillis: Long,
    val durationMinutes: Long,
    val isNap: Boolean,
    val source: SleepSource,
    val qualityScore: Float?,
)

/** The current estimate, overwritten on each recomputation. History lives in [ModelSnapshotEntity]. */
@Entity(tableName = "model_state")
data class ModelStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ROW_ID,
    val estimatedDlmoMillis: Long,
    val sleepDebtHours: Float,
    val socialJetlagHours: Float,
    val confidence: Float,
    val lastUpdatedMillis: Long,
)

/** Past model estimates, for the progress timeline (FR-8) and the data export. */
@Entity(tableName = "model_snapshot", indices = [Index("capturedAtMillis")])
data class ModelSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAtMillis: Long,
    val estimatedDlmoMillis: Long,
    val sleepDebtHours: Float,
    val socialJetlagHours: Float,
    val confidence: Float,
)

@Entity(tableName = "schedule_goal")
data class ScheduleGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String?,
    val targetWakeMinuteOfDay: Int,
    val targetDate: LocalDate,
    val status: GoalStatus,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "daily_plan",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class DailyPlanEntity(
    @PrimaryKey val date: LocalDate,
    val goalId: Long,
    val lightStartMillis: Long,
    val lightEndMillis: Long,
    val avoidLightStartMillis: Long,
    val avoidLightEndMillis: Long,
    val caffeineCutoffMillis: Long,
    val targetBedtimeMillis: Long,
    val napWindowStartMillis: Long?,
    val napWindowEndMillis: Long?,
    val napMaxDurationMinutes: Int?,
)

@Entity(tableName = "energy_forecast")
data class EnergyForecastEntity(
    @PrimaryKey val date: LocalDate,
    val hourlyAlertness: List<Float>,
)

@Entity(tableName = "check_in")
data class CheckInEntity(
    @PrimaryKey val date: LocalDate,
    val reportedAlertness: Int,
    val predictedAlertness: Float,
    val delta: Float,
    val appliedCorrectionMinutes: Int,
)

@Entity(tableName = "screen_time_snapshot")
data class ScreenTimeSnapshotEntity(
    @PrimaryKey val date: LocalDate,
    val totalMinutes: Int,
    val afterDimmingMinutes: Int,
)

@Entity(tableName = "sleep_score")
data class SleepScoreEntity(
    @PrimaryKey val date: LocalDate,
    val overallScore: Int,
    val durationSubScore: Int,
    val timingSubScore: Int,
    val fragmentationSubScore: Int,
    val consistencySubScore: Int,
    val label: String,
)

@Entity(tableName = "eating_window")
data class EatingWindowEntity(
    @PrimaryKey val date: LocalDate,
    val firstMealMillis: Long,
    val lastMealMillis: Long,
    val source: MealTimeSource,
)
