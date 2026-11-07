package com.circapin.data.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import java.time.Duration
import kotlin.time.toKotlinInstant

private val AWAKE_STAGES = setOf(
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
)

/**
 * Health Connect has no nap flag, so sessions up to [SleepRecord.MAX_NAP_DURATION] count as naps.
 * A very short night is therefore also recorded as a nap.
 */
internal fun SleepSessionRecord.toSleepRecord(): SleepRecord {
    val onset = startTime.toKotlinInstant()
    val wake = endTime.toKotlinInstant()
    return SleepRecord(
        externalId = metadata.id,
        sleepOnset = onset,
        wakeTime = wake,
        isNap = wake - onset <= SleepRecord.MAX_NAP_DURATION,
        source = SleepSource.HEALTH_CONNECT,
        qualityScore = asleepShare(),
    )
}

/** Null when the source recorded no stages, since fragmentation is then unknown. */
private fun SleepSessionRecord.asleepShare(): Float? {
    if (stages.isEmpty()) return null
    val sessionMillis = Duration.between(startTime, endTime).toMillis()
    if (sessionMillis == 0L) return null
    val awakeMillis = stages
        .filter { it.stage in AWAKE_STAGES }
        .sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
    return (1f - awakeMillis.toFloat() / sessionMillis).coerceIn(0f, 1f)
}
