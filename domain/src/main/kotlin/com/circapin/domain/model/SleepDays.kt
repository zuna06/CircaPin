package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One stretch of sleep, possibly with short awakenings. [asleep] excludes the awake gaps. */
data class SleepBout(val onset: Instant, val wake: Instant, val asleep: Duration) {
    val midpoint: Instant get() = onset + (wake - onset) / 2
}

/**
 * Everything slept on one sleep day, which runs from noon to noon, so a night belongs to the evening it started.
 *
 * @property main the longest bout, used for timing. Null when the day only has naps, since a nap says little
 *   about the body clock.
 * @property totalAsleep all sleep that day, naps included, used for sleep debt.
 */
data class SleepDay(val date: LocalDate, val main: SleepBout?, val totalAsleep: Duration) {
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

    private val NOON_SHIFT = 12.hours

    /** The sleep day containing [instant]: noon on that date until noon the next day. */
    fun sleepDayOf(instant: Instant, timeZone: TimeZone): LocalDate =
        (instant - NOON_SHIFT).toLocalDateTime(timeZone).date

    fun from(records: List<SleepRecord>, timeZone: TimeZone): List<SleepDay> =
        bouts(records)
            .groupBy { sleepDayOf(it.midpoint, timeZone) }
            .map { (date, bouts) ->
                val longest = bouts.maxBy { it.asleep }
                SleepDay(
                    date = date,
                    main = longest.takeIf { it.asleep >= MIN_MAIN_SLEEP },
                    totalAsleep = bouts.fold(Duration.ZERO) { total, bout -> total + bout.asleep },
                )
            }
            .sortedBy { it.date }

    /**
     * Merges overlapping records first, so sleep reported twice (two apps, or a manual and a synced record)
     * counts once, then joins pieces separated by short awakenings.
     */
    fun bouts(records: List<SleepRecord>): List<SleepBout> {
        val intervals = mutableListOf<Pair<Instant, Instant>>()
        for (record in records.sortedBy { it.sleepOnset }) {
            val last = intervals.lastOrNull()
            if (last != null && record.sleepOnset <= last.second) {
                intervals[intervals.lastIndex] = last.first to maxOf(last.second, record.wakeTime)
            } else {
                intervals += record.sleepOnset to record.wakeTime
            }
        }

        val bouts = mutableListOf<SleepBout>()
        for ((start, end) in intervals) {
            val last = bouts.lastOrNull()
            if (last != null && start - last.wake < MAX_GAP_WITHIN_BOUT) {
                bouts[bouts.lastIndex] = SleepBout(last.onset, end, last.asleep + (end - start))
            } else {
                bouts += SleepBout(start, end, end - start)
            }
        }
        return bouts
    }
}
