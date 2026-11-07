package com.circapin.domain.model

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.datetime.LocalDate

/**
 * Accumulated sleep deficit over the last [WINDOW_DAYS] sleep days.
 *
 * Each night adds its shortfall against the need, and a long night pays debt back, but never below zero, so
 * sleep can't be banked. A day with no main sleep isn't charged the need, because a missing log is not a
 * sleepless night, but any naps on it still pay debt back. That covers today's nap before tonight's sleep.
 */
object SleepDebtCalculator {
    const val WINDOW_DAYS = 14

    fun debtHours(days: List<SleepDay>, need: Duration, today: LocalDate): Float {
        var debt = Duration.ZERO
        days
            .filter { today.toEpochDays() - it.date.toEpochDays() in 0 until WINDOW_DAYS }
            .sortedBy { it.date }
            .forEach { day ->
                val charged = if (day.main != null) need else Duration.ZERO
                debt = (debt + charged - day.totalAsleep).coerceAtLeast(Duration.ZERO)
            }
        return debt.toDouble(DurationUnit.HOURS).toFloat()
    }
}
