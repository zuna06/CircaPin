package com.circapin.ui.sleeplog

import app.cash.turbine.test
import com.circapin.SleepSync
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.SleepSyncResult
import com.circapin.ui.FakeSleepRepository
import com.circapin.ui.MainDispatcherExtension
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SleepLogViewModelTest {
    private val onset = Instant.parse("2026-11-06T04:00:00Z")
    private val record = SleepRecord(
        sleepOnset = onset,
        wakeTime = onset + 8.hours,
        isNap = false,
        source = SleepSource.MANUAL,
    )

    private fun syncFor(repository: FakeSleepRepository) = SleepSync(repository, Clock.System)

    @Test
    fun `opening the log doesn't start a sync of its own`() {
        val repository = FakeSleepRepository()

        SleepLogViewModel(syncFor(repository), repository)

        assertEquals(0, repository.syncCount)
    }

    @Test
    fun `before the first sync finishes the log shows it in progress`() = runTest {
        val repository = FakeSleepRepository()

        SleepLogViewModel(syncFor(repository), repository).state.test {
            assertEquals(SyncStatus.SYNCING, expectMostRecentItem().syncStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync results map to what the screen shows`() = runTest {
        val cases = mapOf(
            SleepSyncResult.Synced(3) to SyncStatus.SYNCED,
            SleepSyncResult.NotConnected to SyncStatus.NOT_CONNECTED,
            SleepSyncResult.Unavailable to SyncStatus.UNAVAILABLE,
            SleepSyncResult.Failed to SyncStatus.FAILED,
        )

        cases.forEach { (result, expected) ->
            val repository = FakeSleepRepository(syncResult = result)
            val sync = syncFor(repository)
            val viewModel = SleepLogViewModel(sync, repository)
            sync.sync()

            viewModel.state.test {
                assertEquals(expected, expectMostRecentItem().syncStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `new records reach the screen`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = SleepLogViewModel(syncFor(repository), repository)

        viewModel.state.test {
            assertEquals(emptyList<SleepRecord>(), expectMostRecentItem().records)

            repository.add(record)

            assertEquals(listOf(record), awaitItem().records)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the Sync button syncs`() {
        val repository = FakeSleepRepository()
        val viewModel = SleepLogViewModel(syncFor(repository), repository)

        viewModel.sync()

        assertEquals(1, repository.syncCount)
    }
}
