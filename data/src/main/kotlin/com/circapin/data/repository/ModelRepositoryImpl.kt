package com.circapin.data.repository

import com.circapin.data.local.ModelStateDao
import com.circapin.data.local.toDomain
import com.circapin.data.local.toEntity
import com.circapin.domain.entities.ModelState
import com.circapin.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRepositoryImpl(private val dao: ModelStateDao) : ModelRepository {
    override fun observeState(): Flow<ModelState?> = dao.observe().map { it?.toDomain() }

    override suspend fun save(state: ModelState) {
        dao.save(state.toEntity())
    }
}
