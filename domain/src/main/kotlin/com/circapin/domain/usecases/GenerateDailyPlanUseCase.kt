package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.NapWindow
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.TimeWindow
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.model.PersonalSleepTimingEstimator
import com.circapin.domain.model.SleepDays
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.CircularTime
import com.circapin.domain.model.GoalProgress
import com.circapin.domain.model.ShiftPlanner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class GeneratedDay(val plan: DailyPlan, val eatingWindow: EatingWindow, val progress: GoalProgress)

/**
 * Builds one day's plan from the model and the goal.
 *
 * Light timing follows the phase response curve. Light in the hours after the body temperature minimum moves the
 * clock earlier, and light before it moves the clock later, so an advancing plan never puts bright light before
 * the minimum. The minimum is taken as 7.5 hours after DLMO, about 2.5 hours before habitual wake.
 */
class GenerateDailyPlanUseCase {
    operator fun invoke(
        model: ModelState,
        goal: ScheduleGoal,
        profile: UserProfile,
        lastNightScore: SleepScore?,
        date: LocalDate,
        timeZone: TimeZone,
        records: List<SleepRecord> = emptyList(),
    ): GeneratedDay {
        require(profile.sleepNeedHours.isFinite() && profile.sleepNeedHours in 4f..12f)
        val dlmo = CircadianModel.dlmoClockTime(model, timeZone)
        val timing = PersonalSleepTimingEstimator.estimate(records.filter { it.wakeTime <= model.lastUpdated },
            CircularTime.toLocalTime(CircularTime.minuteOfDay(dlmo) + 120), date, timeZone)
        // Translate the observed behavioral bedtime into the planner's two-hour phase convention.
        // This does not overwrite the physiological model with a sleep log.
        val scheduleDlmo = CircularTime.toLocalTime(CircularTime.minuteOfDay(timing.onset) - 120)
        val lowScore = lastNightScore != null && lastNightScore.overall < LOW_SCORE
        val baseCap = if (timing.nights < 3 || timing.irregular || model.confidence < 0.5f || model.sleepDebtHours >= NAP_DEBT_HOURS) 30 else null
        var progress = ShiftPlanner.progress(scheduleDlmo, goal, profile.sleepNeedHours, date, baseCap)
        // Recompute reachability at the reduced pace instead of displaying an optimistic deadline.
        if (lowScore) progress = ShiftPlanner.progress(scheduleDlmo, goal, profile.sleepNeedHours, date,
            maxOf(1, kotlin.math.abs(progress.dailyStepMinutes) / 2))
        val step = progress.dailyStepMinutes

        val need = (profile.sleepNeedHours * 60).toDouble().minutes
        val dlmoMinutes = CircularTime.eveningMinutes(dlmo)
        val lastNightDlmo = CircularTime.onEvening(LocalDate.fromEpochDays(date.toEpochDays() - 1), dlmoMinutes, timeZone)
        val previous = LocalDate.fromEpochDays(date.toEpochDays() - 1)
        val observedWake = SleepDays.from(records.filter { !it.isNap && it.wakeTime <= model.lastUpdated }, timeZone)
            .lastOrNull { it.date == previous }?.main?.wake
            ?.takeIf { it.toLocalDateTime(timeZone).date == date }
        val habitualOnset = CircularTime.onEvening(previous, CircularTime.eveningMinutes(timing.onset), timeZone)
        val wakeToday = observedWake ?: (habitualOnset + need)
        val temperatureMinimum = lastNightDlmo + TEMPERATURE_MINIMUM_AFTER_DLMO
        val tonightDlmo = CircularTime.onEvening(date, CircularTime.eveningMinutes(scheduleDlmo) - step, timeZone)
        val scheduledBedtime = tonightDlmo + SLEEP_ONSET_AFTER_DLMO
        // A very late recorded wake must not leave meals overlapping sleep. Keep at least an
        // eight-hour waking interval as a planning guard, not an estimate of biological sleepiness.
        val bedtime = maxOf(scheduledBedtime, wakeToday + 8.hours)
        val lateWakeAdjustment = bedtime > scheduledBedtime

        // Morning light only helps after the temperature minimum; before it, light moves the clock later.
        val morningLight = maxOf(temperatureMinimum, wakeToday)
        val (seek, avoid) = when {
            step > 0 -> TimeWindow(morningLight, morningLight + 2.hours) to TimeWindow(bedtime - 3.hours, bedtime)
            step < 0 -> TimeWindow(bedtime - 3.hours, bedtime - 1.hours) to TimeWindow(wakeToday, wakeToday + 2.hours)
            else -> TimeWindow(morningLight, morningLight + 1.hours) to TimeWindow(bedtime - 2.hours, bedtime)
        }

        val plan = DailyPlan(
            date = date,
            goalId = goal.id,
            brightLight = seek,
            avoidLight = avoid,
            caffeineCutoff = bedtime - CAFFEINE_CUTOFF_BEFORE_BED,
            targetBedtime = bedtime,
            nap = napWindow(model.sleepDebtHours, lowScore, wakeToday, bedtime),
            targetWakeTime = bedtime + need,
            personalizationNights = timing.nights,
            plannedShiftMinutes = if (lateWakeAdjustment) 0 else step,
            shortfallMinutes = progress.shortfallMinutes,
            lateWakeAdjustment = lateWakeAdjustment,
        )
        val eating = EatingWindow(
            date = date,
            firstMeal = wakeToday + FIRST_MEAL_AFTER_WAKE,
            lastMeal = maxOf(bedtime - LAST_MEAL_BEFORE_BED, wakeToday + FIRST_MEAL_AFTER_WAKE),
        )
        return GeneratedDay(plan, eating, progress)
    }

    /** A nap only when debt is high or last night was poor, and never within 6 hours of bedtime (FR-9). */
    private fun napWindow(debtHours: Float, lowScore: Boolean, wake: Instant, bedtime: Instant): NapWindow? {
        if (debtHours < NAP_DEBT_HOURS && !lowScore) return null
        val start = wake + NAP_AFTER_WAKE
        val end = minOf(start + NAP_WINDOW_LENGTH, bedtime - NO_NAP_BEFORE_BED)
        val length = end - start
        val maxDuration: Duration = when {
            debtHours >= FULL_CYCLE_DEBT_HOURS && length >= FULL_CYCLE_NAP -> FULL_CYCLE_NAP
            length >= SHORT_NAP -> SHORT_NAP
            else -> return null
        }
        return NapWindow(TimeWindow(start, end), maxDuration)
    }

    companion object {
        const val LOW_SCORE = 50
        const val NAP_DEBT_HOURS = 4f
        const val FULL_CYCLE_DEBT_HOURS = 8f
        val SLEEP_ONSET_AFTER_DLMO = 2.hours
        val TEMPERATURE_MINIMUM_AFTER_DLMO = 7.5.hours

        /** With a 6-hour caffeine half-life, stopping 8 hours before bed leaves under half by bedtime. */
        val CAFFEINE_CUTOFF_BEFORE_BED = 8.hours
        val FIRST_MEAL_AFTER_WAKE = 30.minutes
        val LAST_MEAL_BEFORE_BED = 2.5.hours
        val NAP_AFTER_WAKE = 6.hours
        val NAP_WINDOW_LENGTH = 2.hours
        val NO_NAP_BEFORE_BED = 6.hours
        val SHORT_NAP = 20.minutes
        val FULL_CYCLE_NAP = 90.minutes
    }
}
