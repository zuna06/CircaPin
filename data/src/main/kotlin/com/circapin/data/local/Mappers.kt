package com.circapin.data.local

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import kotlin.time.Instant

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
    onboardingComplete = onboardingComplete,
)

internal fun UserProfile.toEntity() = UserProfileEntity(
    chronotypeScore = chronotypeScore,
    sleepNeedHours = sleepNeedHours,
    onboardingComplete = onboardingComplete,
)

internal fun ModelStateEntity.toDomain() = ModelState(
    estimatedDlmo = Instant.fromEpochMilliseconds(estimatedDlmoMillis),
    sleepDebtHours = sleepDebtHours,
    socialJetlagHours = socialJetlagHours,
    confidence = confidence,
    lastUpdated = Instant.fromEpochMilliseconds(lastUpdatedMillis),
)

internal fun ModelState.toEntity() = ModelStateEntity(
    estimatedDlmoMillis = estimatedDlmo.toEpochMilliseconds(),
    sleepDebtHours = sleepDebtHours,
    socialJetlagHours = socialJetlagHours,
    confidence = confidence,
    lastUpdatedMillis = lastUpdated.toEpochMilliseconds(),
)
