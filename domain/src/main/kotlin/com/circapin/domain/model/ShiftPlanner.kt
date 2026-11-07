package com.circapin.domain.model

import com.circapin.domain.entities.ScheduleGoal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * How far the body clock is from the goal, and tonight's step toward it.
 *
 * @property shiftMinutes how much earlier DLMO must move. Negative means it must move later.
 * @property nightsLeft nights before the goal morning, tonight included. Zero on or after the goal date.
 * @property dailyStepMinutes how much earlier tonight's DLMO should be than the current estimate.
 * @property shortfallMinutes how far short the goal will still be at the safe pace. Zero when it's reachable.
 */
data class GoalProgress(
    val currentDlmo: LocalTime,
    val targetDlmo: LocalTime,
    val shiftMinutes: Int,
    val nightsLeft: Int,
    val dailyStepMinutes: Int,
    val shortfallMinutes: Int,
)

/** Paces the shift toward a goal, within the outline's physiological limits. */
object ShiftPlanner {
    const val MAX_ADVANCE_MINUTES_PER_DAY = 60
    const val MAX_DELAY_MINUTES_PER_DAY = 90

    /** Close enough to count as on schedule. */
    const val ON_TARGET_MINUTES = 15

    private const val SLEEP_ONSET_AFTER_DLMO_MINUTES = 120.0

    /** DLMO that leads to waking at [targetWake] after a full night: wake minus sleep need minus two hours. */
    fun targetDlmo(targetWake: LocalTime, sleepNeedHours: Float): LocalTime =
        CircularTime.toLocalTime(
            CircularTime.minuteOfDay(targetWake) - sleepNeedHours * 60 - SLEEP_ONSET_AFTER_DLMO_MINUTES,
        )

    fun progress(currentDlmo: LocalTime, goal: ScheduleGoal, sleepNeedHours: Float, planDate: LocalDate): GoalProgress {
        val target = targetDlmo(goal.targetWakeTime, sleepNeedHours)
        val shift = CircularTime.difference(
            to = CircularTime.minuteOfDay(currentDlmo),
            from = CircularTime.minuteOfDay(target),
        ).roundToInt()
        val magnitude = abs(shift)
        val nights = (goal.targetDate.toEpochDays() - planDate.toEpochDays()).toInt().coerceAtLeast(0)
        val cap = if (shift > 0) MAX_ADVANCE_MINUTES_PER_DAY else MAX_DELAY_MINUTES_PER_DAY

        val step = when {
            magnitude <= ON_TARGET_MINUTES -> magnitude
            nights == 0 -> min(magnitude, cap)
            else -> min(cap, ceil(magnitude.toDouble() / nights).toInt())
        }
        val shortfall = when {
            magnitude <= ON_TARGET_MINUTES -> 0
            nights == 0 -> magnitude
            else -> (magnitude - cap * nights).coerceAtLeast(0)
        }
        return GoalProgress(currentDlmo, target, shift, nights, step * shift.sign, shortfall)
    }
}
