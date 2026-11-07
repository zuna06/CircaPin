package com.circapin.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.repository.HealthConnectStatus
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Where synced sleep sessions come from. Lets repository tests run without Health Connect. */
interface SleepSessionSource {
    suspend fun status(): HealthConnectStatus

    suspend fun readSessions(from: Instant, to: Instant): List<SleepRecord>
}

/** Read-only wrapper around Health Connect. CircaPin never writes to it. */
class HealthConnectSleepSource(private val context: Context) : SleepSessionSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun status(): HealthConnectStatus {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HealthConnectStatus.UNAVAILABLE
        }
        val granted = client.permissionController.getGrantedPermissions()
        return if (granted.containsAll(PERMISSIONS)) HealthConnectStatus.CONNECTED else HealthConnectStatus.NOT_CONNECTED
    }

    override suspend fun readSessions(from: Instant, to: Instant): List<SleepRecord> {
        val timeRange = TimeRangeFilter.between(from.toJavaInstant(), to.toJavaInstant())
        val records = mutableListOf<SleepRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange, pageToken = pageToken),
            )
            response.records.mapTo(records) { it.toSleepRecord() }
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    companion object {
        val PERMISSIONS = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
    }
}
