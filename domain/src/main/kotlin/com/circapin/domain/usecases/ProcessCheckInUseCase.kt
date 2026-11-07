package com.circapin.domain.usecases

import com.circapin.domain.entities.CheckIn
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.ModelState
import com.circapin.domain.repository.CheckInRepository
import com.circapin.domain.repository.ModelRepository
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Records a morning check-in and, when two consecutive mismatches point the same way, nudges the DLMO.
 *
 * A mismatch is any difference of [MISMATCH_THRESHOLD] or more between the reported alertness (scaled to 0-1) and
 * the model's prediction for the check-in hour. The correction is 15 min for a 1-point gap and 30 min for 2+.
 */
class ProcessCheckInUseCase(
    private val checkIns: CheckInRepository,
    private val models: ModelRepository,
) {
    private val mutex = Mutex()
    suspend operator fun invoke(
        alertness: Int,
        date: LocalDate,
        forecast: EnergyForecast,
        model: ModelState,
        wakeHour: Int,
    ): CheckIn = mutex.withLock {
        require(alertness in 1..5)
        checkIns.get(date)?.let { return@withLock it }
        val predicted = forecast.hourlyAlertness.getOrElse(wakeHour.coerceIn(0, 23)) { 0.5f }
        val reported = (alertness - 1) / 4f
        val delta = reported - predicted

        val yesterday = checkIns.get(LocalDate.fromEpochDays(date.toEpochDays() - 1))
        val previous = checkIns.observeAll().first().sumOf { it.appliedCorrectionMinutes }
        val correction = (previous + correction(delta, yesterday)).coerceIn(-180, 180) - previous

        val checkIn = CheckIn(
            date = date,
            alertness = alertness,
            predictedAlertness = predicted,
            appliedCorrectionMinutes = correction,
        )
        checkIns.save(checkIn)

        if (correction != 0) {
            val adjusted = model.copy(
                estimatedDlmo = model.estimatedDlmo + correction.minutes,
                lastUpdated = model.lastUpdated,
            )
            models.save(adjusted)
        }
        checkIn
    }

    companion object {
        const val MISMATCH_THRESHOLD = 0.2f
        const val SMALL_CORRECTION_MINUTES = 15
        const val LARGE_CORRECTION_MINUTES = 30

        internal fun correction(todayDelta: Float, yesterday: CheckIn?): Int {
            if (abs(todayDelta) < MISMATCH_THRESHOLD) return 0
            if (yesterday == null) return 0
            val yesterdayDelta = (yesterday.alertness - 1) / 4f - yesterday.predictedAlertness
            if (abs(yesterdayDelta) < MISMATCH_THRESHOLD) return 0
            if (todayDelta.sign != yesterdayDelta.sign) return 0

            val points = abs(todayDelta * 4)
            val magnitude = if (points >= 2) LARGE_CORRECTION_MINUTES else SMALL_CORRECTION_MINUTES
            return if (todayDelta < 0) magnitude else -magnitude
        }
    }
}
