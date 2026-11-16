package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalTime

/** One stretch of sleep, possibly with short awakenings. [asleep] excludes the awake gaps. */
data class SleepBout(val onset: Instant, val wake: Instant, val asleep: Duration, val debtAsleep: Duration = asleep) {
    val midpoint: Instant get() = onset + (wake - onset) / 2
}

/**
 * Everything slept on one sleep day, which runs from noon to noon, so a night belongs to the evening it started.
 *
 * @property main the longest bout, used for timing. Null when the day only has naps, since a nap says little
 *   about the body clock.
 * @property totalAsleep union of session durations, excluding gaps between sessions.
 * @property debtAsleep estimated time asleep after available stage fractions, naps included.
 */
data class SleepDay(val date: LocalDate, val main: SleepBout?, val totalAsleep: Duration, val debtAsleep: Duration = totalAsleep) {
    /** Friday and Saturday nights, which come before days usually free of classes. */
    val isFreeNight: Boolean get() = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
}

/** Turns raw records into sleep days the model can use. */
object SleepDays {
    /** Gaps shorter than this are awakenings within one bout, as when a wearable splits a night. */
    val MAX_GAP_WITHIN_BOUT = 1.hours

    /**
     * Shortest bout that can time the body clock. Classification ignores the nap flag, because Health Connect
     * has none and a short night would otherwise be lost.
     */
    val MIN_MAIN_SLEEP = 2.hours

    /** The sleep day containing [instant]: noon on that date until noon the next day. */
    fun sleepDayOf(instant: Instant, timeZone: TimeZone): LocalDate {
        val local = instant.toLocalDateTime(timeZone)
        return if (local.time < LocalTime(12, 0)) LocalDate.fromEpochDays(local.date.toEpochDays() - 1) else local.date
    }

    fun from(records: List<SleepRecord>, timeZone: TimeZone): List<SleepDay> =
        bouts(records)
            .groupBy { sleepDayOf(it.midpoint, timeZone) }
            .map { (date, bouts) ->
                val longest = bouts.maxBy { it.asleep }
                SleepDay(
                    date = date,
                    main = longest.takeIf { it.asleep >= MIN_MAIN_SLEEP },
                    totalAsleep = bouts.fold(Duration.ZERO) { total, bout -> total + bout.asleep },
                    debtAsleep = bouts.fold(Duration.ZERO) { total, bout -> total + bout.debtAsleep },
                )
            }
            .sortedBy { it.date }

    /**
     * Merges overlapping records first, so sleep reported twice (two apps, or a manual and a synced record)
     * counts once, then joins pieces separated by short awakenings.
     */
    fun bouts(records: List<SleepRecord>): List<SleepBout> {
        val intervals = mutableListOf<Pair<Instant, Instant>>()
        val sources = mutableListOf<MutableList<SleepRecord>>()
        for (record in records.sortedBy { it.sleepOnset }) {
            val last = intervals.lastOrNull()
            if (last != null && record.sleepOnset <= last.second) {
                intervals[intervals.lastIndex] = last.first to maxOf(last.second, record.wakeTime)
                sources.last().add(record)
            } else {
                intervals += record.sleepOnset to record.wakeTime
                sources.add(mutableListOf(record))
            }
        }

        val bouts = mutableListOf<SleepBout>()
        for ((index, interval) in intervals.withIndex()) {
            val (start, end) = interval
            val debtAsleep = estimatedAsleep(sources[index], start, end)
            val last = bouts.lastOrNull()
            if (last != null && start - last.wake < MAX_GAP_WITHIN_BOUT) {
                bouts[bouts.lastIndex] = SleepBout(last.onset, end, last.asleep + (end - start), last.debtAsleep + debtAsleep)
            } else {
                bouts += SleepBout(start, end, end - start, debtAsleep)
            }
        }
        return bouts
    }
    /** Session-wide stage fractions estimate duration, not the timing of individual awakenings.
     * Known fractions take precedence over unstaged/manual duplicates. Conflicting sources use
     * the highest known asleep fraction, avoiding exaggerated debt from duplicate reports.
     */
    private fun estimatedAsleep(records: List<SleepRecord>, start: Instant, end: Instant): Duration {
        val overlapping = records.filter { it.sleepOnset < end && it.wakeTime > start }
        val boundaries = (listOf(start, end) + overlapping.flatMap {
            listOf(maxOf(start, it.sleepOnset), minOf(end, it.wakeTime))
        }).distinct().sorted()
        return boundaries.zipWithNext().fold(Duration.ZERO) { total, (a, b) ->
            val measured = overlapping.filter { it.sleepOnset < b && it.wakeTime > a }
                .mapNotNull { it.qualityScore?.takeIf { share -> share.isFinite() && share in 0f..1f } }
            total + (b - a) * (measured.maxOrNull()?.toDouble() ?: 1.0)
        }
    }

}
