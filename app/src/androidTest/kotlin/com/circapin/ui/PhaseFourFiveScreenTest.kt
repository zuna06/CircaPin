package com.circapin.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.*
import com.circapin.ui.settings.*
import com.circapin.ui.checkin.*
import com.circapin.ui.progress.*
import com.circapin.ui.theme.CircaPinTheme
import kotlin.time.Instant
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseFourFiveScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun independentMealAndDimmingTogglesHaveAccessibleLabels() {
        var chosen = ReminderPreferences()
        compose.setContent {
            var prefs by remember { mutableStateOf(ReminderPreferences()) }
            CircaPinTheme { SettingsScreen(SettingsUiState(prefs = prefs), { prefs = it; chosen = it }) }
        }
        compose.onNodeWithText("First meal").performScrollTo().performClick()
        compose.onNodeWithText("Screen dimming").performScrollTo().performClick()
        assertTrue(chosen.firstMealReminder)
        assertFalse(chosen.mealReminder)
        assertFalse(chosen.dimReminder)
    }
    @Test fun deleteAllRequiresTheConfirmationDialog() {
        var deleted = false
        compose.setContent { CircaPinTheme { SettingsScreen(SettingsUiState(), {}, onDeleteAll = { deleted = true }) } }
        compose.onNodeWithText("Delete all data").performScrollTo().performClick()
        assertFalse(deleted)
        compose.onNodeWithText("This removes all sleep", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Delete all data")[2].performClick()
        assertTrue(deleted)
    }
    @Test fun checkInHasFiveChoicesAndADoneAction() {
        var choice = 0
        var done = false
        compose.setContent { CircaPinTheme { CheckInScreen(CheckInUiState(loading = false), { choice = it }, { done = true }) } }
        compose.onNodeWithText("Groggy").performClick()
        assertEquals(2, choice)
        compose.onNodeWithText("Back").performScrollTo().performClick()
        assertTrue(done)
    }
    @Test fun unavailableCheckInExplainsTheWindow() {
        compose.setContent { CircaPinTheme { CheckInScreen(CheckInUiState(loading = false, available = false), {}, {}) } }
        compose.onNodeWithText("Check in between", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Wide awake").assertDoesNotExist()
    }
    @Test fun progressShowsDebtJetlagAverageAndTargetDate() {
        val now = Instant.parse("2026-11-10T09:00:00Z")
        val goal = ScheduleGoal(id = 1, targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 15), createdAt = now)
        compose.setContent { CircaPinTheme { ProgressScreen(ProgressUiState(loading = false, goal = goal, sleepDebtHours = 2f, socialJetlagHours = 1f, averageScore = 75, totalDays = 5)) } }
        compose.onNodeWithText("Sleep debt 2.0 h", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("7-day average sleep score: 75 / 100").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Target date:", substring = true).performScrollTo().assertIsDisplayed()
    }
}
