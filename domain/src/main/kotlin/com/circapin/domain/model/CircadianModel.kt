package com.circapin.domain.model

import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** Combines phase, sleep debt and social jetlag into one estimate. Pure, so it runs and tests on the JVM. */
object CircadianModel {
    private const val NOON_MINUTES = 12 * 60

    fun compute(records: List<SleepRecord>, profile: UserProfile, now: Instant, timeZone: TimeZone): ModelState {
        val today = SleepDays.sleepDayOf(now, timeZone)
        val days = SleepDays.from(records.filter { it.wakeTime <= now }, timeZone)
        val baseline = ChronotypeQuestionnaire.baselineDlmo(profile.chronotypeScore)
        val phase = PhaseEstimator.estimate(days, baseline, today, timeZone)

        // DLMO falls in the evening of the sleep day; an estimate after midnight belongs to the next calendar date.
        val dlmoMinutes = CircularTime.minuteOfDay(phase.dlmo)
        val dlmoDate = if (dlmoMinutes >= NOON_MINUTES) today else today.plus(DatePeriod(days = 1))

        return ModelState(
            estimatedDlmo = LocalDateTime(dlmoDate, phase.dlmo).toInstant(timeZone),
            sleepDebtHours = SleepDebtCalculator.debtHours(days, (profile.sleepNeedHours * 60).toDouble().minutes, today),
            socialJetlagHours = SocialJetlagDetector.hours(days, today, timeZone),
            confidence = phase.confidence,
            lastUpdated = now,
        )
    }

    /** Clock time of the estimate in [timeZone]. */
    fun dlmoClockTime(state: ModelState, timeZone: TimeZone) =
        CircularTime.toLocalTime(CircularTime.minuteOfDay(state.estimatedDlmo, timeZone))
}
