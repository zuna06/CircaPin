package com.circapin.domain.usecases

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.UserProfile
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal object PlanFixtures {
    val UTC = TimeZone.UTC
    val TUESDAY = LocalDate(2026, 11, 10)
    val NOW: Instant = Instant.parse("2026-11-10T09:00:00Z")

    fun model(dlmo: LocalTime, debtHours: Float = 0f) = ModelState(
        estimatedDlmo = LocalDateTime(TUESDAY, dlmo).toInstant(UTC),
        sleepDebtHours = debtHours,
        socialJetlagHours = 0f,
        confidence = 1f,
        lastUpdated = NOW,
    )

    fun goal(wake: LocalTime = LocalTime(7, 0), nights: Int = 14, id: Long = 1) = ScheduleGoal(
        id = id,
        targetWakeTime = wake,
        targetDate = LocalDate.fromEpochDays(TUESDAY.toEpochDays() + nights),
        createdAt = NOW,
    )

    fun profile(sleepNeedHours: Float = 8f) = UserProfile(chronotypeScore = 14, sleepNeedHours = sleepNeedHours, onboardingComplete = true)

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(UTC)

    fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
}
