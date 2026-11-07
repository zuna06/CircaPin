package com.circapin.data.repository

import com.circapin.data.local.UserProfileDao
import com.circapin.data.local.toDomain
import com.circapin.data.local.toEntity
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserProfileRepositoryImpl(private val dao: UserProfileDao) : UserProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = dao.observe().map { it?.toDomain() }

    override suspend fun save(profile: UserProfile) {
        dao.upsert(profile.toEntity())
    }
}
