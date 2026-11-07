package com.circapin.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.model.ChronotypeQuestion
import com.circapin.domain.model.ChronotypeQuestionnaire
import com.circapin.domain.model.ChronotypeResult
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.domain.repository.SleepRepository
import com.circapin.domain.usecases.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** @property result set once every question is answered, which moves onboarding to its final step. */
data class OnboardingUiState(
    val questionIndex: Int = 0,
    val answers: Map<ChronotypeQuestion, Int> = emptyMap(),
    val result: ChronotypeResult? = null,
    val healthConnectStatus: HealthConnectStatus? = null,
    val isFinished: Boolean = false,
) {
    val question: ChronotypeQuestion get() = ChronotypeQuestion.entries[questionIndex]
    val selectedOption: Int? get() = answers[question]
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val sleepRepository: SleepRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun selectOption(option: Int) {
        _state.update { it.copy(answers = it.answers + (it.question to option)) }
    }

    fun next() {
        val current = _state.value
        if (current.selectedOption == null) return
        if (current.questionIndex < ChronotypeQuestion.entries.lastIndex) {
            _state.update { it.copy(questionIndex = it.questionIndex + 1) }
        } else {
            _state.update { it.copy(result = ChronotypeQuestionnaire.score(it.answers)) }
            refreshHealthConnectStatus()
        }
    }

    fun back() {
        _state.update {
            when {
                it.result != null -> it.copy(result = null)
                it.questionIndex > 0 -> it.copy(questionIndex = it.questionIndex - 1)
                else -> it
            }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val status = sleepRepository.healthConnectStatus()
            _state.update { it.copy(healthConnectStatus = status) }
        }
    }

    fun finish() {
        viewModelScope.launch {
            completeOnboarding(_state.value.answers)
            _state.update { it.copy(isFinished = true) }
        }
    }
}
