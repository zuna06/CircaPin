package com.circapin.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.GoalReason
import com.circapin.domain.model.GoalProgress
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalSetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val form = GoalForm(wakeTime = LocalTime(7, 0), date = LocalDate(2026, 11, 24))

    private fun show(
        state: GoalSetupUiState,
        onReason: (GoalReason) -> Unit = {},
        onSave: () -> Unit = {},
        onCancel: (() -> Unit)? = null,
    ) {
        compose.setContent {
            GoalSetupScreen(state, onWakeTime = {}, onDate = {}, onReason = onReason, onSave = onSave, onCancel = onCancel)
        }
    }

    @Test
    fun aDateTooEarlyIsExplained() {
        show(GoalSetupUiState(form.copy(dateTooEarly = true), LocalDate(2026, 11, 11)))

        compose.onNodeWithText("Choose a date after today.").assertIsDisplayed()
    }

    @Test
    fun thePreviewDescribesTheShiftAndAnyShortfall() {
        val preview = GoalProgress(LocalTime(2, 0), LocalTime(21, 0), 300, 3, 60, 120)
        show(GoalSetupUiState(form, LocalDate(2026, 11, 11), preview))

        compose.onNodeWithText("about 5 h earlier, around 60 min a night over 3 nights", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("about 2 h short", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reasonsAndSaveReachTheCaller() {
        val reasons = mutableListOf<GoalReason>()
        var saved = false
        show(GoalSetupUiState(form, LocalDate(2026, 11, 11)), onReason = { reasons += it }, onSave = { saved = true })

        compose.onNodeWithText("Finals").performScrollTo().performClick()
        compose.onNodeWithText("Save goal").performScrollTo().performClick()

        assertEquals(listOf(GoalReason.FINALS), reasons)
        assertTrue(saved)
    }

    @Test
    fun cancelOnlyAppearsWhenEditing() {
        var cancelled = false
        show(GoalSetupUiState(form, LocalDate(2026, 11, 11)), onCancel = { cancelled = true })

        compose.onNodeWithText("Cancel").performClick()

        assertTrue(cancelled)
    }
}
