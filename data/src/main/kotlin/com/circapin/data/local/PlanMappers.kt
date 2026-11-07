package com.circapin.data.local

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.GoalReason
import com.circapin.domain.entities.NapWindow
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNote
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.entities.TimeWindow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime

private const val MINUTES_PER_HOUR = 60

/** Sub-scores have no data yet, stored in non-null columns. */
private const val NO_SCORE = -1

private fun Long.toInstant() = Instant.fromEpochMilliseconds(this)

internal fun ScheduleGoalEntity.toDomain() = ScheduleGoal(
    id = id,
    reason = label?.let { name -> GoalReason.entries.firstOrNull { it.name == name } },
    targetWakeTime = LocalTime(targetWakeMinuteOfDay / MINUTES_PER_HOUR, targetWakeMinuteOfDay % MINUTES_PER_HOUR),
    targetDate = targetDate,
    status = status,
    createdAt = createdAtMillis.toInstant(),
)

/** The reason is stored by name in the label column, so it can be shown in any language. */
internal fun ScheduleGoal.toEntity() = ScheduleGoalEntity(
    id = id,
    label = reason?.name,
    targetWakeMinuteOfDay = targetWakeTime.hour * MINUTES_PER_HOUR + targetWakeTime.minute,
    targetDate = targetDate,
    status = status,
    createdAtMillis = createdAt.toEpochMilliseconds(),
)

internal fun DailyPlanEntity.toDomain() = DailyPlan(
    date = date,
    goalId = goalId,
    brightLight = TimeWindow(lightStartMillis.toInstant(), lightEndMillis.toInstant()),
    avoidLight = TimeWindow(avoidLightStartMillis.toInstant(), avoidLightEndMillis.toInstant()),
    caffeineCutoff = caffeineCutoffMillis.toInstant(),
    targetBedtime = targetBedtimeMillis.toInstant(),
    nap = if (napWindowStartMillis != null && napWindowEndMillis != null && napMaxDurationMinutes != null) {
        NapWindow(
            TimeWindow(napWindowStartMillis.toInstant(), napWindowEndMillis.toInstant()),
            napMaxDurationMinutes.minutes,
        )
    } else {
        null
    },
)

internal fun DailyPlan.toEntity() = DailyPlanEntity(
    date = date,
    goalId = goalId,
    lightStartMillis = brightLight.start.toEpochMilliseconds(),
    lightEndMillis = brightLight.end.toEpochMilliseconds(),
    avoidLightStartMillis = avoidLight.start.toEpochMilliseconds(),
    avoidLightEndMillis = avoidLight.end.toEpochMilliseconds(),
    caffeineCutoffMillis = caffeineCutoff.toEpochMilliseconds(),
    targetBedtimeMillis = targetBedtime.toEpochMilliseconds(),
    napWindowStartMillis = nap?.window?.start?.toEpochMilliseconds(),
    napWindowEndMillis = nap?.window?.end?.toEpochMilliseconds(),
    napMaxDurationMinutes = nap?.maxDuration?.inWholeMinutes?.toInt(),
)

internal fun EatingWindowEntity.toDomain() =
    EatingWindow(date, firstMealMillis.toInstant(), lastMealMillis.toInstant(), source)

internal fun EatingWindow.toEntity() =
    EatingWindowEntity(date, firstMeal.toEpochMilliseconds(), lastMeal.toEpochMilliseconds(), source)

internal fun EnergyForecastEntity.toDomain() = EnergyForecast(date, hourlyAlertness)

internal fun EnergyForecast.toEntity() = EnergyForecastEntity(date, hourlyAlertness)

internal fun SleepScoreEntity.toDomain() = SleepScore(
    date = date,
    overall = overallScore,
    duration = durationSubScore,
    timing = timingSubScore.takeIf { it != NO_SCORE },
    fragmentation = fragmentationSubScore.takeIf { it != NO_SCORE },
    consistency = consistencySubScore.takeIf { it != NO_SCORE },
    note = label.split(":").let { parts ->
        SleepScoreNote(
            kind = SleepScoreNoteKind.entries.firstOrNull { it.name == parts[0] } ?: SleepScoreNoteKind.ON_TRACK,
            minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    },
)

/** The note is stored as KIND:minutes, and turned into words by the app. */
internal fun SleepScore.toEntity() = SleepScoreEntity(
    date = date,
    overallScore = overall,
    durationSubScore = duration,
    timingSubScore = timing ?: NO_SCORE,
    fragmentationSubScore = fragmentation ?: NO_SCORE,
    consistencySubScore = consistency ?: NO_SCORE,
    label = "${note.kind.name}:${note.minutes}",
)
