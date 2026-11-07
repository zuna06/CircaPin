package com.circapin.domain.usecases

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.SleepRepository
import kotlin.time.Clock
import kotlin.time.Instant

enum class SleepEntryProblem { WAKE_NOT_AFTER_ONSET, IN_FUTURE, TOO_LONG, OVERLAPS_EXISTING }

sealed interface LogSleepResult {
    data object Saved : LogSleepResult
    data class Rejected(val problem: SleepEntryProblem) : LogSleepResult
}

/** Validates a manual sleep or nap entry and stores it. */
class LogSleepUseCase(
    private val repository: SleepRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(onset: Instant, wake: Instant, isNap: Boolean): LogSleepResult {
        val maxDuration = if (isNap) SleepRecord.MAX_NAP_DURATION else SleepRecord.MAX_SLEEP_DURATION
        val problem = when {
            wake <= onset -> SleepEntryProblem.WAKE_NOT_AFTER_ONSET
            wake > clock.now() -> SleepEntryProblem.IN_FUTURE
            wake - onset > maxDuration -> SleepEntryProblem.TOO_LONG
            repository.hasOverlap(onset, wake) -> SleepEntryProblem.OVERLAPS_EXISTING
            else -> null
        }
        if (problem != null) return LogSleepResult.Rejected(problem)

        repository.add(
            SleepRecord(sleepOnset = onset, wakeTime = wake, isNap = isNap, source = SleepSource.MANUAL),
        )
        return LogSleepResult.Saved
    }
}
