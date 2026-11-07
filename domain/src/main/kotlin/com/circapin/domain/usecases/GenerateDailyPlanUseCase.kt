package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.NapWindow
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.TimeWindow
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
    ): GeneratedDay {
        val dlmo = CircadianModel.dlmoClockTime(model, timeZone)
        val progress = ShiftPlanner.progress(dlmo, goal, profile.sleepNeedHours, date)
        val lowScore = lastNightScore != null && lastNightScore.overall < LOW_SCORE
        // A poor night makes shifting harder, so the plan eases off for a day (FR-11).
        val step = if (lowScore) progress.dailyStepMinutes / 2 else progress.dailyStepMinutes

        val need = (profile.sleepNeedHours * 60).toDouble().minutes
        val dlmoMinutes = CircularTime.eveningMinutes(dlmo)
        val lastNightDlmo = CircularTime.onEvening(LocalDate.fromEpochDays(date.toEpochDays() - 1), dlmoMinutes, timeZone)
        val wakeToday = lastNightDlmo + SLEEP_ONSET_AFTER_DLMO + need
        val temperatureMinimum = lastNightDlmo + TEMPERATURE_MINIMUM_AFTER_DLMO
        val tonightDlmo = CircularTime.onEvening(date, dlmoMinutes - step, timeZone)
        val bedtime = tonightDlmo + SLEEP_ONSET_AFTER_DLMO

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
