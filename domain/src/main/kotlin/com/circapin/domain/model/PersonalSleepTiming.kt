package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class PersonalSleepTiming(val onset: LocalTime, val nights: Int, val irregular: Boolean)

/** Behavioral schedule estimate, not a new measurement of melatonin onset or sleep need. */
object PersonalSleepTimingEstimator {
    fun estimate(records: List<SleepRecord>, modelOnset: LocalTime, date: LocalDate, zone: TimeZone): PersonalSleepTiming {
        // One main bout per sleep day. Do not learn bedtime from naps, future entries or stale history.
        val nights = SleepDays.from(records.filter { !it.isNap }, zone)
            .filter { date.toEpochDays() - it.date.toEpochDays() in 1..14 }
            .mapNotNull { it.main }.takeLast(7)
        val anchor = CircularTime.minuteOfDay(modelOnset)
        val offsets = nights.map { CircularTime.difference(CircularTime.minuteOfDay(it.onset, zone), anchor) }.sorted()
        if (offsets.size < 3) return PersonalSleepTiming(modelOnset, offsets.size, false)
        val median = median(offsets)
        val deviation = median(offsets.map { abs(it - median) }.sorted())
        // Blend in the observed routine gradually; a single late night cannot move the whole plan.
        val weight = offsets.size / 7.0
        val adjustment = (median.coerceIn(-90.0, 90.0) * weight).roundToInt()
        return PersonalSleepTiming(CircularTime.toLocalTime(anchor + adjustment), offsets.size, deviation > 60)
    }

    private fun median(sorted: List<Double>): Double =
        if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
}
