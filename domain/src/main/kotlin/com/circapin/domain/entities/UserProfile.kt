package com.circapin.domain.entities

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Created during onboarding. The target fields stay null until goal setup exists. */
data class UserProfile(
    val chronotypeScore: Int,
    val sleepNeedHours: Float = DEFAULT_SLEEP_NEED_HOURS,
    val targetWakeTime: LocalTime? = null,
    val targetDate: LocalDate? = null,
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_SLEEP_NEED_HOURS = 8f
    }
}
