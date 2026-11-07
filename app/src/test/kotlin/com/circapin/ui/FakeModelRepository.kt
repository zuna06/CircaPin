package com.circapin.ui

import com.circapin.domain.entities.ModelState
import com.circapin.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeModelRepository : ModelRepository {
    val state = MutableStateFlow<ModelState?>(null)
    val saved = mutableListOf<ModelState>()

    override fun observeState(): Flow<ModelState?> = state

    override suspend fun save(state: ModelState) {
        saved += state
        this.state.value = state
    }
}
