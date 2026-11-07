package com.circapin.domain.repository

import com.circapin.domain.entities.SleepRecord
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

enum class HealthConnectStatus { CONNECTED, NOT_CONNECTED, UNAVAILABLE }

sealed interface SleepSyncResult {
    data class Synced(val recordCount: Int) : SleepSyncResult
    data object NotConnected : SleepSyncResult
    data object Unavailable : SleepSyncResult
    data object Failed : SleepSyncResult
}

interface SleepRepository {
    /** All records, newest first. */
    fun observeRecords(): Flow<List<SleepRecord>>

    suspend fun add(record: SleepRecord)

    suspend fun hasOverlap(start: Instant, end: Instant): Boolean

    suspend fun healthConnectStatus(): HealthConnectStatus

    suspend fun syncFromHealthConnect(): SleepSyncResult
}
