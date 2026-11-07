package com.circapin.data.repository

import com.circapin.data.local.ModelSnapshotDao
import com.circapin.data.local.ModelSnapshotEntity
import com.circapin.domain.repository.ModelSnapshot
import com.circapin.domain.repository.SnapshotRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SnapshotRepositoryImpl(private val dao: ModelSnapshotDao) : SnapshotRepository {

    override fun observeAll(): Flow<List<ModelSnapshot>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun firstSince(instant: Instant): ModelSnapshot? =
        dao.firstSince(instant.toEpochMilliseconds())?.toDomain()
}

private fun ModelSnapshotEntity.toDomain() = ModelSnapshot(
    capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
    estimatedDlmo = Instant.fromEpochMilliseconds(estimatedDlmoMillis),
    sleepDebtHours = sleepDebtHours,
    socialJetlagHours = socialJetlagHours,
    confidence = confidence,
)
