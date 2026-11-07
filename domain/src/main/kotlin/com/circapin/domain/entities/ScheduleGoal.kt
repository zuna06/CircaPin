package com.circapin.domain.entities

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

enum class GoalReason { INTERNSHIP, FINALS, CLASS_SCHEDULE, TRAVEL, OTHER }

/** Wake up at [targetWakeTime] by the morning of [targetDate]. */
data class ScheduleGoal(
    val id: Long = 0,
    val reason: GoalReason? = null,
    val targetWakeTime: LocalTime,
    val targetDate: LocalDate,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Instant,
)
