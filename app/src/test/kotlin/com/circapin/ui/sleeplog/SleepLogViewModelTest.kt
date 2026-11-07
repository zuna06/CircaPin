package com.circapin.ui.sleeplog

import app.cash.turbine.test
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.repository.SleepSyncResult
import com.circapin.ui.FakeModelRepository
import com.circapin.ui.FakeSleepRepository
import com.circapin.ui.MainDispatcherExtension
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

    @Test
    fun `syncs on launch`() {
        val repository = FakeSleepRepository()

        SleepLogViewModel(repository, FakeModelRepository())

        assertEquals(1, repository.syncCount)
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
            val viewModel = SleepLogViewModel(FakeSleepRepository(syncResult = result), FakeModelRepository())

            viewModel.state.test {
                assertEquals(expected, expectMostRecentItem().syncStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `new records reach the screen`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = SleepLogViewModel(repository, FakeModelRepository())

        viewModel.state.test {
            assertEquals(emptyList<SleepRecord>(), expectMostRecentItem().records)

            repository.add(record)

            assertEquals(listOf(record), awaitItem().records)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a manual retry syncs again`() {
        val repository = FakeSleepRepository()
        val viewModel = SleepLogViewModel(repository, FakeModelRepository())

        viewModel.sync()

        assertEquals(2, repository.syncCount)
    }
}
