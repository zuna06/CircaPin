package com.circapin.domain.usecases

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.model.AlertnessModel
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.model.CircularTime
import com.circapin.domain.model.SleepDays
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The hour-by-hour alertness curve for a planned day (FR-5).
 *
 * Sleep pressure is simulated over the last three days of recorded sleep. If last night isn't logged yet it is
 * assumed at the habitual time, and tonight is assumed at the planned bedtime.
 */
class ComputeEnergyForecastUseCase {
    operator fun invoke(
        plan: DailyPlan,
        model: ModelState,
        records: List<SleepRecord>,
        sleepNeedHours: Float,
        timeZone: TimeZone,
    ): EnergyForecast {
        val hours = (0 until HOURS_PER_DAY).map { LocalDateTime(plan.date, LocalTime(it, 0)).toInstant(timeZone) }
        val dayStart = hours.first()
        val from = dayStart - HISTORY
        val need = (sleepNeedHours * 60).toDouble().minutes
        val dlmo = CircadianModel.dlmoClockTime(model, timeZone)

        val recorded = SleepDays.bouts(records)
            .filter { it.wake > from && it.onset < dayStart + MORNING_CUTOFF }
            .map { it.onset to it.wake }
        val lastNightLogged = recorded.any { (onset, wake) ->
            wake > dayStart - 6.hours && onset < dayStart + MORNING_CUTOFF && wake - onset >= SleepDays.MIN_MAIN_SLEEP
        }
        val assumedLastNight = if (lastNightLogged) {
            emptyList()
        } else {
            val onset = CircularTime.onEvening(
                LocalDate.fromEpochDays(plan.date.toEpochDays() - 1),
                CircularTime.eveningMinutes(dlmo) + 120,
                timeZone,
            )
            listOf(onset to onset + need)
        }
        val tonight = listOf(plan.targetBedtime to plan.targetBedtime + need)

        return EnergyForecast(
            date = plan.date,
            hourlyAlertness = AlertnessModel.alertness(
                times = hours,
                from = from,
                sleep = recorded + assumedLastNight + tonight,
                dlmo = dlmo,
                debtHours = model.sleepDebtHours,
                timeZone = timeZone,
            ),
        )
    }

    private companion object {
        const val HOURS_PER_DAY = 24
        val HISTORY = 72.hours

        /** Sleep that starts after this point on the plan date isn't last night's. */
        val MORNING_CUTOFF = 14.hours
    }
}
