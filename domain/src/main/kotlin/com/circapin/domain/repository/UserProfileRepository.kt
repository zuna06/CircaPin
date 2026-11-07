package com.circapin.domain.repository

import com.circapin.domain.entities.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    /** Emits null until onboarding has saved a profile. */
    fun observeProfile(): Flow<UserProfile?>

    suspend fun save(profile: UserProfile)
}
