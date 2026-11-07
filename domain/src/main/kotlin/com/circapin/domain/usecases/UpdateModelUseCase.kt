package com.circapin.domain.usecases

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.repository.ModelRepository
import com.circapin.domain.repository.CheckInRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone

/** Recomputes the circadian model from all sleep records and stores the result. */
class UpdateModelUseCase(
    private val models: ModelRepository,
    private val clock: Clock,
    private val checkIns: CheckInRepository? = null,
) {
    suspend operator fun invoke(records: List<SleepRecord>, profile: UserProfile, timeZone: TimeZone) {
        val state = CircadianModel.compute(records, profile, clock.now(), timeZone)
        // Corrections belong to the model's inputs, rather than being lost on the next sync.
        val correction = checkIns?.observeAll()?.first()?.sumOf { it.appliedCorrectionMinutes } ?: 0
        models.save(state.copy(estimatedDlmo = state.estimatedDlmo + correction.coerceIn(-180, 180).minutes))
    }
}
