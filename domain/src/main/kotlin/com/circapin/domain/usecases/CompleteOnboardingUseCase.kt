package com.circapin.domain.usecases

import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.ChronotypeQuestion
import com.circapin.domain.model.ChronotypeQuestionnaire
import com.circapin.domain.repository.UserProfileRepository

/** Scores the questionnaire and creates the user's profile. */
class CompleteOnboardingUseCase(private val profiles: UserProfileRepository) {
    suspend operator fun invoke(answers: Map<ChronotypeQuestion, Int>) {
        val result = ChronotypeQuestionnaire.score(answers)
        profiles.save(UserProfile(chronotypeScore = result.score, onboardingComplete = true))
    }
}
