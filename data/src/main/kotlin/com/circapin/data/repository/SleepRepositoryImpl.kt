package com.circapin.data.repository

import android.os.RemoteException
import com.circapin.data.healthconnect.SleepSessionSource
import com.circapin.data.local.SleepRecordDao
import com.circapin.data.local.toDomain
import com.circapin.data.local.toEntity
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.repository.SleepSyncResult
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SleepRepositoryImpl(
    private val dao: SleepRecordDao,
    private val source: SleepSessionSource,
    private val clock: Clock,
) : SleepRepository {
    override fun observeRecords(): Flow<List<SleepRecord>> =
        dao.observeAll().map { records -> records.map { it.toDomain() } }

    override suspend fun add(record: SleepRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun delete(record: SleepRecord) {
        dao.deleteAndRemember(record.toEntity(), clock.now().toEpochMilliseconds())
    }

    override suspend fun hasOverlap(start: Instant, end: Instant): Boolean =
        dao.hasOverlap(start.toEpochMilliseconds(), end.toEpochMilliseconds())

    override suspend fun healthConnectStatus(): HealthConnectStatus = source.status()

    override suspend fun syncFromHealthConnect(): SleepSyncResult =
        try {
            when (source.status()) {
                HealthConnectStatus.UNAVAILABLE -> SleepSyncResult.Unavailable
                HealthConnectStatus.NOT_CONNECTED -> SleepSyncResult.NotConnected
                HealthConnectStatus.CONNECTED -> {
                    val now = clock.now()
                    val from = now - SYNC_WINDOW
                    val records = source.readSessions(from, now)
                    val superseded = dao.getManualOverlapping(from.toEpochMilliseconds(), now.toEpochMilliseconds())
                        .filter { it.toDomain().isSupersededBy(records) }
                    dao.saveSynced(records.map { it.toEntity() }, superseded.map { it.id })
                    SleepSyncResult.Synced(records.size)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            SleepSyncResult.Failed
        } catch (_: RemoteException) {
            SleepSyncResult.Failed
        } catch (_: SecurityException) {
            SleepSyncResult.Failed
        } catch (_: IllegalStateException) {
            SleepSyncResult.Failed
        }

    private companion object {
        val SYNC_WINDOW = 30.days
    }
}
