package com.circapin.notifications

import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.usecases.GenerateDailyPlanUseCase

/** Shared bounds for scheduling, snoozing and delayed delivery. */
object CheckInTiming {
    fun isOpen(now: Instant, zone: TimeZone): Boolean = now.toLocalDateTime(zone).hour in 4..11

    fun morningWake(meals: EatingWindow?, fallback: Instant): Instant =
        meals?.firstMeal?.minus(GenerateDailyPlanUseCase.FIRST_MEAL_AFTER_WAKE) ?: fallback

    fun target(date: LocalDate, wake: Instant, zone: TimeZone): Instant {
        val wakeOnDate = LocalDateTime(date, wake.toLocalDateTime(zone).time).toInstant(zone)
        val start = LocalDateTime(date, LocalTime(4, 0)).toInstant(zone)
        val end = LocalDateTime(date, LocalTime(11, 59)).toInstant(zone)
        return (wakeOnDate + 15.minutes).coerceIn(start, end)
    }
}
