package com.circapin.domain.repository

import com.circapin.domain.entities.ModelState
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    /** Emits null until the model has run once. */
    fun observeState(): Flow<ModelState?>

    /** Stores the estimate and keeps a history of how it changes. */
    suspend fun save(state: ModelState)
}
