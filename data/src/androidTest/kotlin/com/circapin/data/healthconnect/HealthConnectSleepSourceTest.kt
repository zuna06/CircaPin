package com.circapin.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.HealthConnectStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real Health Connect on the device, using the test APK's write access to seed sessions.
 * The device may hold other apps' sessions, so assertions only look at records these tests wrote.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectSleepSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = HealthConnectSleepSource(context)
    private lateinit var client: HealthConnectClient
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    @Before
    fun setUp() {
        assumeTrue(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "android.permission.health.READ_SLEEP",
            "android.permission.health.WRITE_SLEEP",
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
        ).forEach { automation.grantRuntimePermission(context.packageName, it) }
        client = HealthConnectClient.getOrCreate(context)
    }

    @After
    fun tearDown() = runBlocking {
        client.deleteRecords(
            SleepSessionRecord::class,
            TimeRangeFilter.between(now.minus(40, ChronoUnit.DAYS), now),
        )
    }

    private fun session(start: Instant, end: Instant, stages: List<SleepSessionRecord.Stage> = emptyList()) =
        SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            metadata = Metadata.manualEntry(),
            stages = stages,
        )

    @Test
    fun statusIsConnectedOncePermissionIsGranted() = runBlocking {
        assertEquals(HealthConnectStatus.CONNECTED, source.status())
    }

    @Test
    fun readsWrittenSessionsWithStages() = runBlocking {
        val start = now.minus(10, ChronoUnit.HOURS)
        val end = now.minus(2, ChronoUnit.HOURS)
        val awakeStart = end.minus(2, ChronoUnit.HOURS)
        val id = client.insertRecords(
            listOf(
                session(
                    start,
                    end,
                    listOf(
                        SleepSessionRecord.Stage(start, awakeStart, SleepSessionRecord.STAGE_TYPE_DEEP),
                        SleepSessionRecord.Stage(awakeStart, end, SleepSessionRecord.STAGE_TYPE_AWAKE),
                    ),
                ),
            ),
        ).recordIdsList.single()

        val record = source.readSessions(
            now.minus(1, ChronoUnit.DAYS).toKotlinInstant(),
            now.toKotlinInstant(),
        ).single { it.externalId == id }

        assertEquals(start.toKotlinInstant(), record.sleepOnset)
        assertEquals(end.toKotlinInstant(), record.wakeTime)
        assertEquals(SleepSource.HEALTH_CONNECT, record.source)
        assertFalse(record.isNap)
        assertEquals(0.75f, record.qualityScore)
    }

    @Test
    fun readsEverySessionAcrossPages() = runBlocking {
        val sessionCount = 1100
        val sessions = List(sessionCount) { index ->
            val start = now.minus((index + 1) * 30L, ChronoUnit.MINUTES)
            session(start, start.plus(20, ChronoUnit.MINUTES))
        }
        val ids = sessions.chunked(500).flatMap { client.insertRecords(it).recordIdsList }.toSet()

        val records = source.readSessions(
            now.minus(30, ChronoUnit.DAYS).toKotlinInstant(),
            now.toKotlinInstant(),
        ).filter { it.externalId in ids }

        assertEquals(sessionCount, records.size)
    }
}
