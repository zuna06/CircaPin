package com.circapin.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.circapin.domain.model.ChronotypeQuestion
import com.circapin.domain.model.ChronotypeQuestionnaire
import com.circapin.domain.repository.HealthConnectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(
        state: OnboardingUiState,
        onSelectOption: (Int) -> Unit = {},
        onConnect: () -> Unit = {},
        onFinish: () -> Unit = {},
    ) {
        compose.setContent {
            OnboardingScreen(
                state = state,
                onSelectOption = onSelectOption,
                onNext = {},
                onBack = {},
                onConnect = onConnect,
                onFinish = onFinish,
            )
        }
    }

    private val completed = OnboardingUiState(
        answers = ChronotypeQuestion.entries.associateWith { 0 },
        result = ChronotypeQuestionnaire.score(ChronotypeQuestion.entries.associateWith { 0 }),
    )

    @Test
    fun everyQuestionHasWordingForEachOption() {
        ChronotypeQuestion.entries.forEach { question ->
            assertEquals(
                question.name,
                question.optionPoints.size,
                context.resources.getStringArray(question.optionsRes).size,
            )
        }
    }

    @Test
    fun nextIsDisabledUntilAnOptionIsSelected() {
        show(OnboardingUiState())

        compose.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun nextIsEnabledOnceAnswered() {
        show(OnboardingUiState(answers = mapOf(ChronotypeQuestion.PREFERRED_WAKE_TIME to 1)))

        compose.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun tappingAnOptionReportsItsIndex() {
        var selected = -1
        show(OnboardingUiState(), onSelectOption = { selected = it })

        compose.onNodeWithText("7:45 to 9:45 AM").performClick()

        assertEquals(2, selected)
    }

    @Test
    fun healthConnectCanBeSkipped() {
        var finished = false
        var connected = false
        show(
            completed.copy(healthConnectStatus = HealthConnectStatus.NOT_CONNECTED),
            onConnect = { connected = true },
            onFinish = { finished = true },
        )

        compose.onNodeWithText("Connect Health Connect").performClick()
        assertTrue(connected)

        compose.onNodeWithText("Log sleep manually instead").performClick()
        assertTrue(finished)
    }

    @Test
    fun unavailableHealthConnectFallsBackToManualLogging() {
        var finished = false
        show(completed.copy(healthConnectStatus = HealthConnectStatus.UNAVAILABLE), onFinish = { finished = true })

        compose.onNodeWithText("Connect Health Connect").assertDoesNotExist()
        compose.onNodeWithText("Continue").performClick()

        assertTrue(finished)
    }
}
