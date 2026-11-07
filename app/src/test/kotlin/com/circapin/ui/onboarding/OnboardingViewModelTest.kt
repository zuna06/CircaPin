package com.circapin.ui.onboarding

import com.circapin.domain.entities.UserProfile
import com.circapin.domain.model.ChronotypeQuestion
import com.circapin.domain.model.ChronotypeQuestionnaire
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.CompleteOnboardingUseCase
import com.circapin.ui.FakeSleepRepository
import com.circapin.ui.MainDispatcherExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class OnboardingViewModelTest {
    private class FakeProfiles : UserProfileRepository {
        val profile = MutableStateFlow<UserProfile?>(null)

        override fun observeProfile(): Flow<UserProfile?> = profile

        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
    }

    private val profiles = FakeProfiles()
    private val sleep = FakeSleepRepository(status = HealthConnectStatus.NOT_CONNECTED)
    private val viewModel = OnboardingViewModel(CompleteOnboardingUseCase(profiles), sleep)

    private fun answerEverything() {
        repeat(ChronotypeQuestion.entries.size) {
            viewModel.selectOption(0)
            viewModel.next()
        }
    }

    @Test
    fun `next does nothing until an option is selected`() {
        viewModel.next()
        assertEquals(0, viewModel.state.value.questionIndex)

        viewModel.selectOption(1)
        viewModel.next()
        assertEquals(1, viewModel.state.value.questionIndex)
    }

    @Test
    fun `going back keeps earlier answers`() {
        viewModel.selectOption(2)
        viewModel.next()

        viewModel.back()

        assertEquals(0, viewModel.state.value.questionIndex)
        assertEquals(2, viewModel.state.value.selectedOption)
    }

    @Test
    fun `answering the last question scores it and checks Health Connect`() {
        answerEverything()

        assertNotNull(viewModel.state.value.result)
        assertEquals(HealthConnectStatus.NOT_CONNECTED, viewModel.state.value.healthConnectStatus)
    }

    @Test
    fun `back from the result returns to the last question`() {
        answerEverything()

        viewModel.back()

        assertNull(viewModel.state.value.result)
        assertEquals(ChronotypeQuestion.entries.lastIndex, viewModel.state.value.questionIndex)
    }

    @Test
    fun `the status refreshes after the permission screen`() {
        answerEverything()
        sleep.status = HealthConnectStatus.CONNECTED

        viewModel.refreshHealthConnectStatus()

        assertEquals(HealthConnectStatus.CONNECTED, viewModel.state.value.healthConnectStatus)
    }

    @Test
    fun `finishing saves a completed profile`() {
        answerEverything()
        assertFalse(viewModel.state.value.isFinished)

        viewModel.finish()

        assertTrue(viewModel.state.value.isFinished)
        val profile = profiles.profile.value!!
        assertTrue(profile.onboardingComplete)
        assertEquals(viewModel.state.value.result?.score, profile.chronotypeScore)
        assertTrue(profile.chronotypeScore in ChronotypeQuestionnaire.MIN_SCORE..ChronotypeQuestionnaire.MAX_SCORE)
    }
}
