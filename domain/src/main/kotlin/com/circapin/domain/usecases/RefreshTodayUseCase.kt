package com.circapin.domain.usecases

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.repository.PlanRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps today's plan, forecast and sleep score up to date.
 *
 * The plan is generated once per day and then kept fixed, so its times don't move during the day. Only a goal
 * change regenerates it. Because it's fixed, it waits for a model computed after the latest sleep sync attempt,
 * so last night's data has had its chance to arrive. The sleep score is recomputed each time.
 */
class RefreshTodayUseCase(
    private val plans: PlanRepository,
    private val clock: Clock,
    private val generatePlan: GenerateDailyPlanUseCase = GenerateDailyPlanUseCase(),
    private val computeForecast: ComputeEnergyForecastUseCase = ComputeEnergyForecastUseCase(),
    private val computeScore: ComputeSleepScoreUseCase = ComputeSleepScoreUseCase(),
) {
    /** @param lastSyncAttempt when sleep data was last synced, or null if not yet during this run of the app. */
    suspend operator fun invoke(
        records: List<SleepRecord>,
        profile: UserProfile,
        model: ModelState?,
        goal: ScheduleGoal?,
        lastSyncAttempt: Instant?,
        timeZone: TimeZone,
    ) {
        val date = planDate(clock.now(), timeZone)
        val lastNightPlan = plans.getPlan(LocalDate.fromEpochDays(date.toEpochDays() - 1))
        val score = computeScore(records, date, lastNightPlan, profile.sleepNeedHours, timeZone)
        if (score == null) plans.deleteScore(date) else plans.saveScore(score)

        if (model == null || goal == null) return
        val existing = plans.getPlan(date)
        if (existing != null && existing.goalId == goal.id) {
            // Action times stay fixed, but the forecast reflects late sleep and feedback.
            plans.saveForecast(computeForecast(existing, model, records, profile.sleepNeedHours, timeZone))
            return
        }
        // A plan is kept all day, so don't build it from yesterday's estimate or from data that predates a sync.
        if (model.lastUpdated < LocalDateTime(date, LocalTime(DAY_START_HOUR, 0)).toInstant(timeZone)) return
        if (lastSyncAttempt == null || model.lastUpdated < lastSyncAttempt) return

        val day = generatePlan(model, goal, profile, score, date, timeZone)
        val forecast = computeForecast(day.plan, model, records, profile.sleepNeedHours, timeZone)
        plans.saveDay(day.plan, day.eatingWindow, forecast)
    }

    companion object {
        /** A plan day starts at 4 AM, so someone still up at 1 AM sees the plan they're living in. */
        private const val DAY_START_HOUR = 4
        fun planDate(now: Instant, timeZone: TimeZone): LocalDate {
            val local = now.toLocalDateTime(timeZone)
            return if (local.hour < DAY_START_HOUR) LocalDate.fromEpochDays(local.date.toEpochDays() - 1) else local.date
        }
    }
}
