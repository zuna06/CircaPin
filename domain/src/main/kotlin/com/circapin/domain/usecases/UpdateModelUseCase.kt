package com.circapin.domain.usecases

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.repository.ModelRepository
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/** Recomputes the circadian model from all sleep records and stores the result. */
class UpdateModelUseCase(
    private val models: ModelRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(records: List<SleepRecord>, profile: UserProfile, timeZone: TimeZone) {
        models.save(CircadianModel.compute(records, profile, clock.now(), timeZone))
    }
}
