package com.circapin.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Social jetlag: free-night sleep midpoint minus school-night midpoint, in hours, over the last two weeks.
 * Positive when free nights run later. Zero until there is at least one night of each kind.
 */
object SocialJetlagDetector {
    const val WINDOW_DAYS = 14

    fun hours(days: List<SleepDay>, today: LocalDate, timeZone: TimeZone): Float {
        val recent = days.filter { it.main != null && today.toEpochDays() - it.date.toEpochDays() in 0 until WINDOW_DAYS }
        fun meanMidpoint(nights: List<SleepDay>) =
            CircularTime.weightedMean(nights.map { CircularTime.minuteOfDay(it.main!!.midpoint, timeZone) to 1.0 })

        val free = meanMidpoint(recent.filter { it.isFreeNight }) ?: return 0f
        val school = meanMidpoint(recent.filterNot { it.isFreeNight }) ?: return 0f
        return (CircularTime.difference(free, school) / 60).toFloat()
    }
}
