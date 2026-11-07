package com.circapin.domain.entities

/** Created during onboarding. Schedule targets belong to a goal, not the profile. */
data class UserProfile(
    val chronotypeScore: Int,
    val sleepNeedHours: Float = DEFAULT_SLEEP_NEED_HOURS,
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_SLEEP_NEED_HOURS = 8f
    }
}
