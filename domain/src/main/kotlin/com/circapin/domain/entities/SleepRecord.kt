package com.circapin.domain.entities

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class SleepSource { HEALTH_CONNECT, MANUAL }

/**
 * One sleep session or nap.
 *
 * @property externalId the source's record ID, used to avoid duplicates on re-sync. Null for manual entries.
 * @property qualityScore share of the session spent asleep (0-1). Null when no stage data is available.
 */
data class SleepRecord(
    val id: Long = 0,
    val externalId: String? = null,
    val sleepOnset: Instant,
    val wakeTime: Instant,
    val isNap: Boolean,
    val source: SleepSource,
    val qualityScore: Float? = null,
) {
    val durationMinutes: Long get() = (wakeTime - sleepOnset).inWholeMinutes

    companion object {
        /** Longest session treated as a nap. Also how Health Connect sessions are classified. */
        val MAX_NAP_DURATION = 3.hours

        /** Longest session accepted from manual entry. */
        val MAX_SLEEP_DURATION = 18.hours
    }
}
