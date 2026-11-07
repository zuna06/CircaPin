package com.circapin.ui.sleeplog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepLogScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val onset = Instant.parse("2026-11-06T04:00:00Z")

    private fun show(
        state: SleepLogUiState,
        onConnect: () -> Unit = {},
        onAddEntry: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            SleepLogScreen(state = state, onSync = {}, onConnect = onConnect, onAddEntry = onAddEntry)
        }
    }

    @Test
    fun anEmptyLogSaysSo() {
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.SYNCED))

        compose.onNodeWithText("No sleep logged yet.").assertIsDisplayed()
    }

    @Test
    fun aRecordShowsItsDurationKindSourceAndQuality() {
        val record = SleepRecord(
            id = 1,
            sleepOnset = onset,
            wakeTime = onset + 7.hours + 45.minutes,
            isNap = false,
            source = SleepSource.HEALTH_CONNECT,
            qualityScore = 0.92f,
        )
        show(SleepLogUiState(records = listOf(record), syncStatus = SyncStatus.SYNCED))

        compose.onNodeWithText("7 h 45 min").assertIsDisplayed()
        compose.onNodeWithText("Sleep · Health Connect · 92% asleep").assertIsDisplayed()
    }

    @Test
    fun aManualNapHasNoQuality() {
        val record = SleepRecord(
            id = 1,
            sleepOnset = onset,
            wakeTime = onset + 20.minutes,
            isNap = true,
            source = SleepSource.MANUAL,
        )
        show(SleepLogUiState(records = listOf(record), syncStatus = SyncStatus.SYNCED))

        compose.onNodeWithText("Nap · Manual").assertIsDisplayed()
    }

    @Test
    fun aNewestRecordIsVisibleWhenAddedToAFullList() {
        fun record(id: Long, daysAgo: Int) = SleepRecord(
            id = id,
            sleepOnset = onset - (daysAgo * 24).hours,
            wakeTime = onset - (daysAgo * 24).hours + 8.hours,
            isNap = false,
            source = SleepSource.HEALTH_CONNECT,
        )
        val older = List(20) { record(id = it.toLong() + 1, daysAgo = it + 1) }
        val newest = SleepRecord(
            id = 100,
            sleepOnset = onset,
            wakeTime = onset + 20.minutes,
            isNap = true,
            source = SleepSource.MANUAL,
        )
        var records by mutableStateOf(older)
        compose.setContent {
            SleepLogScreen(
                state = SleepLogUiState(records = records, syncStatus = SyncStatus.SYNCED),
                onSync = {},
                onConnect = {},
                onAddEntry = {},
            )
        }
        compose.waitForIdle()

        records = listOf(newest) + older

        compose.onNodeWithText("Nap · Manual").assertIsDisplayed()
    }

    @Test
    fun theBodyClockCardShowsTheModel() {
        val model = ModelState(
            estimatedDlmo = onset,
            sleepDebtHours = 2.5f,
            socialJetlagHours = 1f,
            confidence = 4f / 7,
            lastUpdated = onset,
        )
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.SYNCED, model = model))

        compose.onNodeWithText("Body clock estimate").assertIsDisplayed()
        compose.onNodeWithText("Melatonin onset around", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sleep debt 2.5 h · Social jetlag 1.0 h").assertIsDisplayed()
        compose.onNodeWithText("Confidence 57%").assertIsDisplayed()
    }

    @Test
    fun withoutSleepDataTheCardSaysItUsesTheQuestionnaire() {
        val model = ModelState(onset, 0f, 0f, confidence = 0f, lastUpdated = onset)
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.SYNCED, model = model))

        compose.onNodeWithText("Based on your questionnaire answers only").assertIsDisplayed()
    }

    @Test
    fun aDisconnectedLogOffersToConnect() {
        var connected = false
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.NOT_CONNECTED), onConnect = { connected = true })

        compose.onNodeWithText("Connect").performClick()

        assertTrue(connected)
    }

    @Test
    fun syncIsDisabledWhileSyncing() {
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.SYNCING))

        compose.onNodeWithText("Sync").assertIsNotEnabled()
    }

    @Test
    fun eachButtonOpensTheRightEntryType() {
        val requested = mutableListOf<Boolean>()
        show(SleepLogUiState(records = emptyList(), syncStatus = SyncStatus.SYNCED), onAddEntry = { requested += it })

        compose.onNodeWithText("Log sleep").performClick()
        compose.onNodeWithText("Log a nap").performClick()

        assertEquals(listOf(false, true), requested)
    }
}
