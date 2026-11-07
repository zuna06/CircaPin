package com.circapin.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * A two-process alertness model, after the Three-Process Model of Alertness (Akerstedt and Folkard), without its
 * sleep inertia term.
 *
 * Process S, sleep pressure, falls exponentially toward a floor while awake and recovers toward a ceiling while
 * asleep. Process C is a 24-hour cosine timed from the user's DLMO. Alertness is S plus C, less a penalty for
 * longer-term sleep debt that a few days of history can't capture.
 *
 * The S/C constants match Åkerstedt, Folkard and Portin (2004), p. A76. The DLMO peak offset, debt penalty,
 * normalization and simplified sleep handling are app-specific assumptions; see docs/model-notes.md.
 */
internal object AlertnessModel {
    const val S_CEILING = 14.3
    const val S_FLOOR = 2.4
    const val WAKE_DECAY_PER_HOUR = 0.0353
    const val SLEEP_RECOVERY_PER_HOUR = 0.381
    const val C_AMPLITUDE = 2.5

    /** The circadian alertness peak, about 4.2 hours before DLMO for someone with a typical 9 PM DLMO. */
    const val PEAK_BEFORE_DLMO_HOURS = 4.2
    const val DEBT_PENALTY_PER_HOUR = 0.15
    const val MAX_DEBT_PENALTY = 2.0

    private val STEP = 5.minutes
    private const val LOWEST = S_FLOOR - C_AMPLITUDE - MAX_DEBT_PENALTY
    private const val HIGHEST = S_CEILING + C_AMPLITUDE

    /**
     * Alertness from 0 to 1 at each of [times], simulated from [from] with a fully rested start.
     * [sleep] lists every period asleep, past and planned. A time during sleep gets the alertness the person
     * would have if they had stayed up instead, so the small hours read as low rather than as recovering.
     */
    fun alertness(
        times: List<Instant>,
        from: Instant,
        sleep: List<Pair<Instant, Instant>>,
        dlmo: LocalTime,
        debtHours: Float,
        timeZone: TimeZone,
    ): List<Float> {
        val penalty = min(MAX_DEBT_PENALTY, debtHours * DEBT_PENALTY_PER_HOUR)
        val peak = CircularTime.minuteOfDay(dlmo) - PEAK_BEFORE_DLMO_HOURS * 60
        val sorted = times.sorted()
        val pressure = HashMap<Instant, Double>()

        var t = from
        var s = S_CEILING
        var stayedUp = S_CEILING
        var next = 0
        val hours = STEP.toDouble(DurationUnit.HOURS)
        while (next < sorted.size) {
            val asleep = sleep.any { (start, end) -> t >= start && t < end }
            while (next < sorted.size && sorted[next] <= t) {
                pressure[sorted[next]] = if (asleep) stayedUp else s
                next++
            }
            stayedUp = S_FLOOR + (stayedUp - S_FLOOR) * exp(-WAKE_DECAY_PER_HOUR * hours)
            s = if (asleep) {
                S_CEILING - (S_CEILING - s) * exp(-SLEEP_RECOVERY_PER_HOUR * hours)
            } else {
                S_FLOOR + (s - S_FLOOR) * exp(-WAKE_DECAY_PER_HOUR * hours)
            }
            if (!asleep) stayedUp = s
            t += STEP
        }

        return times.map { time ->
            val minutesFromPeak = CircularTime.minuteOfDay(time, timeZone) - peak
            val c = C_AMPLITUDE * cos(2 * PI * minutesFromPeak / CircularTime.MINUTES_PER_DAY)
            val a = pressure.getValue(time) + c - penalty
            ((a - LOWEST) / (HIGHEST - LOWEST)).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
