package com.circapin.ui

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSleepRepository(
    var status: HealthConnectStatus = HealthConnectStatus.NOT_CONNECTED,
    var syncResult: SleepSyncResult = SleepSyncResult.Synced(0),
    var overlaps: Boolean = false,
) : SleepRepository {
    val records = MutableStateFlow<List<SleepRecord>>(emptyList())
    var syncCount = 0
        private set

    override fun observeRecords(): Flow<List<SleepRecord>> = records

    override suspend fun add(record: SleepRecord) {
        records.value += record
    }

    override suspend fun hasOverlap(start: Instant, end: Instant) = overlaps

    override suspend fun healthConnectStatus() = status

    override suspend fun syncFromHealthConnect(): SleepSyncResult {
        syncCount++
        return syncResult
    }
}
