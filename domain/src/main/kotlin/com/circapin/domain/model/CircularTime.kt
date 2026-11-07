package com.circapin.domain.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Clock times as minutes on a 24-hour circle, so 11:30 PM and 12:30 AM average to midnight
 * rather than to noon. Students often sleep past midnight, which a plain average gets wrong.
 */
internal object CircularTime {
    const val MINUTES_PER_DAY = 24 * 60
    private const val HALF_DAY = MINUTES_PER_DAY / 2

    fun normalize(minutes: Double): Double = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** Signed shortest distance from [from] to [to], between -12 and +12 hours. */
    fun difference(to: Double, from: Double): Double {
        val forward = normalize(to - from)
        return if (forward > HALF_DAY) forward - MINUTES_PER_DAY else forward
    }

    /** Null when there is nothing to average, or the times cancel out exactly. */
    fun weightedMean(values: List<Pair<Double, Double>>): Double? {
        var x = 0.0
        var y = 0.0
        for ((minutes, weight) in values) {
            val angle = minutes / MINUTES_PER_DAY * 2 * PI
            x += weight * cos(angle)
            y += weight * sin(angle)
        }
        if (x == 0.0 && y == 0.0) return null
        return normalize(atan2(y, x) / (2 * PI) * MINUTES_PER_DAY)
    }

    fun minuteOfDay(instant: Instant, timeZone: TimeZone): Double {
        val time = instant.toLocalDateTime(timeZone).time
        return time.hour * 60.0 + time.minute + time.second / 60.0
    }

    fun minuteOfDay(time: LocalTime): Double = time.hour * 60.0 + time.minute

    fun toLocalTime(minutes: Double): LocalTime {
        val rounded = normalize(minutes).roundToInt() % MINUTES_PER_DAY
        return LocalTime(rounded / 60, rounded % 60)
    }
}
