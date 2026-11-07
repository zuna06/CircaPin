package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNote
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.model.CircularTime
import com.circapin.domain.model.SleepDays
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Scores the night before the morning of `date` from 0 to 100 (FR-11).
 *
 * Four parts, weighted: duration against sleep need (40%), timing against last night's planned bedtime (25%),
 * fragmentation (15%), and consistency with the previous week's onsets (20%). Parts without data are left out
 * and the rest reweighted. Each half hour off target costs 25 points of that part. All provisional.
 */
class ComputeSleepScoreUseCase {
    operator fun invoke(
        records: List<SleepRecord>,
        date: LocalDate,
        lastNightPlan: DailyPlan?,
        sleepNeedHours: Float,
        timeZone: TimeZone,
    ): SleepScore? {
        val days = SleepDays.from(records, timeZone)
        val night = days.firstOrNull { it.date.toEpochDays() == date.toEpochDays() - 1 }?.main ?: return null

        val needMinutes = sleepNeedHours * 60
        val asleepMinutes = night.asleep.toDouble(DurationUnit.MINUTES)
        val duration = (100 * asleepMinutes / needMinutes).coerceAtMost(100.0).roundToInt()

        val lateMinutes = lastNightPlan?.let { (night.onset - it.targetBedtime).toDouble(DurationUnit.MINUTES) }
        val timing = lateMinutes?.let { offTargetScore(abs(it)) }

        val stageQualities = records
            .filter { it.qualityScore != null && it.sleepOnset < night.wake && it.wakeTime > night.onset }
            .map { it.qualityScore!!.toDouble() }
        val boutShare = night.asleep / (night.wake - night.onset)
        val asleepShare = (stageQualities + boutShare).min()
        val fragmentation = if (stageQualities.isEmpty() && boutShare >= 1.0) null else shareScore(asleepShare)

        val previousOnsets = days
            .filter { it.date.toEpochDays() in (date.toEpochDays() - 8) until (date.toEpochDays() - 1) }
            .mapNotNull { it.main?.onset }
            .map { CircularTime.minuteOfDay(it, timeZone) to 1.0 }
        val irregularMinutes = CircularTime.weightedMean(previousOnsets)?.let { usual ->
            abs(CircularTime.difference(CircularTime.minuteOfDay(night.onset, timeZone), usual))
        }
        val consistency = irregularMinutes?.let { offTargetScore(it) }

        val parts = listOfNotNull(
            duration to DURATION_WEIGHT,
            timing?.let { it to TIMING_WEIGHT },
            fragmentation?.let { it to FRAGMENTATION_WEIGHT },
            consistency?.let { it to CONSISTENCY_WEIGHT },
        )
        val overall = (parts.sumOf { (score, weight) -> score * weight } / parts.sumOf { it.second }).roundToInt()

        // The note names the weakest part, with its size in minutes where that applies.
        val weakest = listOfNotNull(
            Triple(SleepScoreNoteKind.SHORT, duration, (needMinutes - asleepMinutes).roundToInt()),
            timing?.let {
                val late = lateMinutes.roundToInt()
                Triple(if (late >= 0) SleepScoreNoteKind.LATE else SleepScoreNoteKind.EARLY, it, abs(late))
            },
            fragmentation?.let { Triple(SleepScoreNoteKind.BROKEN, it, 0) },
            consistency?.let { Triple(SleepScoreNoteKind.IRREGULAR, it, irregularMinutes.roundToInt()) },
        ).minBy { it.second }
        val note = if (weakest.second >= ON_TRACK) {
            SleepScoreNote(SleepScoreNoteKind.ON_TRACK)
        } else {
            SleepScoreNote(weakest.first, weakest.third)
        }

        return SleepScore(date, overall, duration, timing, fragmentation, consistency, note)
    }

    private fun offTargetScore(minutesOff: Double) = (100 - minutesOff * POINTS_PER_MINUTE_OFF).coerceAtLeast(0.0).roundToInt()

    /** 95% or more of the night asleep scores 100, falling to 0 at half the night. */
    private fun shareScore(share: Double) = (100 * (share - 0.5) / 0.45).coerceIn(0.0, 100.0).roundToInt()

    companion object {
        const val DURATION_WEIGHT = 0.40
        const val TIMING_WEIGHT = 0.25
        const val FRAGMENTATION_WEIGHT = 0.15
        const val CONSISTENCY_WEIGHT = 0.20
        const val POINTS_PER_MINUTE_OFF = 25.0 / 30
        const val ON_TRACK = 85
    }
}
