package com.circapin.domain.entities

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class TimeWindow(val start: Instant, val end: Instant)

data class NapWindow(val window: TimeWindow, val maxDuration: Duration)

/**
 * One day's actions, for the calendar [date]: light in the morning or evening, caffeine, and tonight's bedtime.
 * Generated once per day and kept fixed, so times don't move under the user.
 */
data class DailyPlan(
    val date: LocalDate,
    val goalId: Long,
    val brightLight: TimeWindow,
    val avoidLight: TimeWindow,
    val caffeineCutoff: Instant,
    val targetBedtime: Instant,
    val nap: NapWindow?,
    val targetWakeTime: Instant? = null,
    val personalizationNights: Int = 0,
    val plannedShiftMinutes: Int = 0,
    val shortfallMinutes: Int = 0,
    val lateWakeAdjustment: Boolean = false,
) {
    /** A planning window, not a claim that sleep onset can be predicted to the minute. */
    val bedtimeWindow: TimeWindow get() = TimeWindow(targetBedtime - 15.minutes, targetBedtime + 15.minutes)
    val sleepWindow: TimeWindow? get() = targetWakeTime?.let { TimeWindow(targetBedtime, it) }
    val windDown: Instant get() = targetBedtime - 45.minutes
}

/** When to eat. Only timing matters for the body clock, so nothing about what is eaten is tracked. */
data class EatingWindow(
    val date: LocalDate,
    val firstMeal: Instant,
    val lastMeal: Instant,
    val source: MealTimeSource = MealTimeSource.PLAN,
) {
    /** Three meals by default. Windows are deterministic from the persisted daily anchors. */
    private val mealLength: Duration get() = minOf(1.hours, (lastMeal - firstMeal).coerceAtLeast(Duration.ZERO) / 5)
    val breakfast: TimeWindow get() = TimeWindow(firstMeal, firstMeal + mealLength)
    val lunch: TimeWindow get() {
        val middle = firstMeal + (lastMeal - firstMeal) / 2
        return TimeWindow(middle - mealLength / 2, middle + mealLength / 2)
    }
    val dinner: TimeWindow get() = TimeWindow(lastMeal - mealLength, lastMeal)
}

/** Predicted alertness (0-1) for each local hour of [date], from midnight. */
data class EnergyForecast(val date: LocalDate, val hourlyAlertness: List<Float>)

/** Everything shown for one day on the home screen. */
data class DayPlan(val plan: DailyPlan, val eatingWindow: EatingWindow?, val forecast: EnergyForecast?)

enum class SleepScoreNoteKind { ON_TRACK, SHORT, LATE, EARLY, BROKEN, IRREGULAR }

/** What most held the score back. [minutes] is the size of it, where that applies. */
data class SleepScoreNote(val kind: SleepScoreNoteKind, val minutes: Int = 0)

/**
 * Last night's sleep, scored 0-100, for the morning of [date]. A sub-score is null when there's no data for it,
 * such as timing before any plan exists.
 */
data class SleepScore(
    val date: LocalDate,
    val overall: Int,
    val duration: Int,
    val timing: Int?,
    val fragmentation: Int?,
    val consistency: Int?,
    val note: SleepScoreNote,
)
