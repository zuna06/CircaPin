package com.circapin.domain.repository

import com.circapin.domain.entities.ModelState
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

data class ModelSnapshot(
    val capturedAt: Instant,
    val estimatedDlmo: Instant,
    val sleepDebtHours: Float,
    val socialJetlagHours: Float,
    val confidence: Float,
)

interface SnapshotRepository {
    fun observeAll(): Flow<List<ModelSnapshot>>

    suspend fun firstSince(instant: Instant): ModelSnapshot?
}
