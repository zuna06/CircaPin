package com.circapin.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.data.healthconnect.SleepSessionSource
import com.circapin.data.local.CircaPinDatabase
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.SleepSyncResult
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@RepositoryTest.now
    }

    private class FakeSource(
        var status: HealthConnectStatus = HealthConnectStatus.CONNECTED,
        var sessions: List<SleepRecord> = emptyList(),
        var failure: Exception? = null,
    ) : SleepSessionSource {
        var requestedFrom: Instant? = null

        override suspend fun status() = status

        override suspend fun readSessions(from: Instant, to: Instant): List<SleepRecord> {
            requestedFrom = from
            failure?.let { throw it }
            return sessions
        }
    }

    private lateinit var db: CircaPinDatabase
    private val source = FakeSource()
    private lateinit var repository: SleepRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CircaPinDatabase::class.java,
        ).build()
        repository = SleepRepositoryImpl(db.sleepRecordDao(), source, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun synced(id: String, hoursAgo: Int = 12) = SleepRecord(
        externalId = id,
        sleepOnset = now - hoursAgo.hours,
        wakeTime = now - (hoursAgo - 8).hours,
        isNap = false,
        source = SleepSource.HEALTH_CONNECT,
        qualityScore = 0.9f,
    )

    @Test
    fun sourceDeletionRemovesOnlySyncedRecordsInTheReadWindow() = runTest {
        source.sessions = listOf(synced("removed"))
        repository.syncFromHealthConnect()
        source.sessions = emptyList()
        repository.syncFromHealthConnect()
        assertTrue(repository.observeRecords().first().isEmpty())
    }

    @Test
    fun syncStoresSessionsAndCanBeRepeated() = runTest {
        source.sessions = listOf(synced("a"), synced("b", hoursAgo = 36))

        assertEquals(SleepSyncResult.Synced(2), repository.syncFromHealthConnect())
        assertEquals(SleepSyncResult.Synced(2), repository.syncFromHealthConnect())

        val records = repository.observeRecords().first()
        assertEquals(listOf("a", "b"), records.map { it.externalId })
        assertEquals(0.9f, records.first().qualityScore)
        assertEquals(now - 30.days, source.requestedFrom)
    }

    @Test
    fun syncKeepsManualRecords() = runTest {
        val manual = SleepRecord(
            sleepOnset = now - 60.hours,
            wakeTime = now - 52.hours,
            isNap = false,
            source = SleepSource.MANUAL,
        )
        repository.add(manual)
        source.sessions = listOf(synced("a"))

        repository.syncFromHealthConnect()

        assertEquals(
            listOf(SleepSource.HEALTH_CONNECT, SleepSource.MANUAL),
            repository.observeRecords().first().map { it.source },
        )
    }

    private fun manual(fromHoursAgo: Int, toHoursAgo: Int) = SleepRecord(
        sleepOnset = now - fromHoursAgo.hours,
        wakeTime = now - toHoursAgo.hours,
        isNap = false,
        source = SleepSource.MANUAL,
    )

    private fun session(id: String, fromHoursAgo: Int, toHoursAgo: Int) =
        manual(fromHoursAgo, toHoursAgo).copy(externalId = id, source = SleepSource.HEALTH_CONNECT)

    @Test
    fun aSyncedNightReplacesTheManualEntryForThatNight() = runTest {
        repository.add(manual(fromHoursAgo = 13, toHoursAgo = 5))
        source.sessions = listOf(session("a", fromHoursAgo = 12, toHoursAgo = 4))

        repository.syncFromHealthConnect()
        repository.syncFromHealthConnect()

        assertEquals(listOf("a"), repository.observeRecords().first().map { it.externalId })
    }

    @Test
    fun fragmentsOfANightReplaceItTogether() = runTest {
        repository.add(manual(fromHoursAgo = 12, toHoursAgo = 4))
        source.sessions = listOf(
            session("a", fromHoursAgo = 12, toHoursAgo = 9),
            session("b", fromHoursAgo = 8, toHoursAgo = 5),
        )

        repository.syncFromHealthConnect()

        assertEquals(listOf("b", "a"), repository.observeRecords().first().map { it.externalId })
    }

    @Test
    fun aPartlyRecordedNightKeepsTheManualEntry() = runTest {
        repository.add(manual(fromHoursAgo = 12, toHoursAgo = 4))
        source.sessions = listOf(session("a", fromHoursAgo = 12, toHoursAgo = 10))

        repository.syncFromHealthConnect()

        assertEquals(
            setOf(SleepSource.MANUAL, SleepSource.HEALTH_CONNECT),
            repository.observeRecords().first().map { it.source }.toSet(),
        )
    }

    @Test
    fun aFailedSyncRemovesNothing() = runTest {
        repository.add(manual(fromHoursAgo = 12, toHoursAgo = 4))
        source.sessions = listOf(session("a", fromHoursAgo = 12, toHoursAgo = 4))
        source.failure = IOException("Health Connect rate limit")

        repository.syncFromHealthConnect()

        assertEquals(
            listOf(SleepSource.MANUAL),
            repository.observeRecords().first().map { it.source },
        )
    }

    @Test
    fun syncReportsWhyItCouldNotRun() = runTest {
        source.status = HealthConnectStatus.NOT_CONNECTED
        assertEquals(SleepSyncResult.NotConnected, repository.syncFromHealthConnect())

        source.status = HealthConnectStatus.UNAVAILABLE
        assertEquals(SleepSyncResult.Unavailable, repository.syncFromHealthConnect())

        assertNull(source.requestedFrom)
        assertTrue(repository.observeRecords().first().isEmpty())
    }

    @Test
    fun aReadFailureIsReportedNotThrown() = runTest {
        source.failure = IOException("Health Connect rate limit")
        assertEquals(SleepSyncResult.Failed, repository.syncFromHealthConnect())

        source.failure = SecurityException("Permission revoked")
        assertEquals(SleepSyncResult.Failed, repository.syncFromHealthConnect())
    }

    @Test
    fun overlapUsesStoredRecords() = runTest {
        source.sessions = listOf(synced("a"))
        repository.syncFromHealthConnect()

        assertTrue(repository.hasOverlap(now - 10.hours, now - 9.hours))
        assertFalse(repository.hasOverlap(now - 3.hours, now - 1.hours))
    }

    @Test
    fun profileSurvivesARoundTrip() = runTest {
        val repository = UserProfileRepositoryImpl(db.userProfileDao())
        assertNull(repository.observeProfile().first())

        val profile = UserProfile(chronotypeScore = 14, sleepNeedHours = 7.5f, onboardingComplete = true)
        repository.save(profile)

        assertEquals(profile, repository.observeProfile().first())
    }

    @Test
    fun aDeletedSyncedNightStaysDeleted() = runTest {
        source.sessions = listOf(synced("a"), synced("b", hoursAgo = 36))
        repository.syncFromHealthConnect()

        repository.delete(repository.observeRecords().first().single { it.externalId == "a" })
        repository.syncFromHealthConnect()

        assertEquals(listOf("b"), repository.observeRecords().first().map { it.externalId })
    }

    @Test
    fun modelStateSurvivesARoundTrip() = runTest {
        val repository = ModelRepositoryImpl(db.modelStateDao())
        assertNull(repository.observeState().first())

        val state = ModelState(
            estimatedDlmo = now + 10.hours,
            sleepDebtHours = 2.5f,
            socialJetlagHours = 1.25f,
            confidence = 0.6f,
            lastUpdated = now,
        )
        repository.save(state)

        assertEquals(state, repository.observeState().first())
    }
}
