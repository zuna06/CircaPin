package com.circapin.data.local

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import kotlin.time.Instant
import kotlinx.datetime.LocalTime

private const val MINUTES_PER_HOUR = 60

internal fun SleepRecordEntity.toDomain() = SleepRecord(
    id = id,
    externalId = externalId,
    sleepOnset = Instant.fromEpochMilliseconds(sleepOnsetMillis),
    wakeTime = Instant.fromEpochMilliseconds(wakeTimeMillis),
    isNap = isNap,
    source = source,
    qualityScore = qualityScore,
)

internal fun SleepRecord.toEntity() = SleepRecordEntity(
    id = id,
    externalId = externalId,
    sleepOnsetMillis = sleepOnset.toEpochMilliseconds(),
    wakeTimeMillis = wakeTime.toEpochMilliseconds(),
    durationMinutes = durationMinutes,
    isNap = isNap,
    source = source,
    qualityScore = qualityScore,
)

internal fun UserProfileEntity.toDomain() = UserProfile(
    chronotypeScore = chronotypeScore,
    sleepNeedHours = sleepNeedHours,
    targetWakeTime = targetWakeMinuteOfDay?.let { LocalTime(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) },
    targetDate = targetDate,
    onboardingComplete = onboardingComplete,
)

internal fun UserProfile.toEntity() = UserProfileEntity(
    chronotypeScore = chronotypeScore,
    sleepNeedHours = sleepNeedHours,
    targetWakeMinuteOfDay = targetWakeTime?.let { it.hour * MINUTES_PER_HOUR + it.minute },
    targetDate = targetDate,
    onboardingComplete = onboardingComplete,
)
