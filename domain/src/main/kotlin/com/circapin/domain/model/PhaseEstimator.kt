package com.circapin.domain.model

import kotlin.math.min
import kotlin.math.pow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * @property dlmo estimated clock time of melatonin onset.
 * @property confidence 0 with no nights, reaching 1 after [PhaseEstimator.NIGHTS_FOR_FULL_CONFIDENCE].
 */
data class PhaseEstimate(val dlmo: LocalTime, val confidence: Float, val nights: Int)

/**
 * Estimates DLMO from recent sleep onsets and the chronotype baseline.
 *
 * DLMO is taken as [DLMO_LEAD_MINUTES] before the weighted average onset. Recent nights count more, and free
 * nights count more than school nights, since the body clock is closer to the free-night pattern. The baseline
 * acts as one extra night that fades as real nights accumulate, so day one has an estimate and a week of data
 * overrides it. All the constants are provisional and should be tuned against real check-in data in Phase 4.
 */
object PhaseEstimator {
    const val DLMO_LEAD_MINUTES = 120.0
    const val WINDOW_DAYS = 14
    const val HALF_LIFE_DAYS = 2.0
    const val FREE_NIGHT_WEIGHT = 3.0
    const val NIGHTS_FOR_FULL_CONFIDENCE = 7

    fun estimate(days: List<SleepDay>, baseline: LocalTime, today: LocalDate, timeZone: TimeZone): PhaseEstimate {
        val nights = days.filter { it.main != null && it.date.ageIn(today) in 0 until WINDOW_DAYS }
        val baselineMinutes = CircularTime.minuteOfDay(baseline)
        val weighted = nights.map { day ->
            val onset = CircularTime.minuteOfDay(day.main!!.onset, timeZone)
            val recency = 0.5.pow(day.date.ageIn(today) / HALF_LIFE_DAYS)
            onset to recency * if (day.isFreeNight) FREE_NIGHT_WEIGHT else 1.0
        }
        val meanOnset = CircularTime.weightedMean(weighted)
            ?: return PhaseEstimate(baseline, confidence = 0f, nights = 0)

        val dataDlmo = meanOnset - DLMO_LEAD_MINUTES
        val dataWeight = weighted.sumOf { it.second }
        val baselineWeight = 0.5.pow(nights.size / HALF_LIFE_DAYS)
        val pull = baselineWeight / (baselineWeight + dataWeight)
        val dlmo = dataDlmo + CircularTime.difference(baselineMinutes, dataDlmo) * pull

        return PhaseEstimate(
            dlmo = CircularTime.toLocalTime(dlmo),
            confidence = min(1f, nights.size.toFloat() / NIGHTS_FOR_FULL_CONFIDENCE),
            nights = nights.size,
        )
    }

    private fun LocalDate.ageIn(today: LocalDate): Long = today.toEpochDays() - toEpochDays()
}
