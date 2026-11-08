package com.circapin.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.circapin.ui.components.ScreenColumn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.model.ChronotypeQuestion
import com.circapin.domain.model.ChronotypeResult
import com.circapin.domain.model.ChronotypeType
import com.circapin.domain.repository.HealthConnectStatus
import com.circapin.ui.components.rememberHealthConnectPermissionRequest
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime

@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberHealthConnectPermissionRequest(viewModel::refreshHealthConnectStatus)

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onFinished()
    }
    BackHandler(enabled = state.result != null || state.questionIndex > 0, onBack = viewModel::back)

    OnboardingScreen(
        state = state,
        onSelectOption = viewModel::selectOption,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onConnect = requestPermission,
        onFinish = viewModel::finish,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onSelectOption: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        key(state.questionIndex, state.result != null) {
            ScreenColumn(Modifier.safeDrawingPadding(), maxWidth = 600.dp) {
                val result = state.result
                if (result == null) {
                    QuestionStep(state, onSelectOption, onNext, onBack)
                } else {
                    ConnectStep(result, state.healthConnectStatus, onConnect, onFinish)
                }
            }
        }
    }
}

@Composable
private fun QuestionStep(
    state: OnboardingUiState,
    onSelectOption: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val question = state.question
    LinearProgressIndicator(progress = { (state.questionIndex + 1f) / ChronotypeQuestion.entries.size }, modifier = Modifier.fillMaxWidth().height(4.dp))
    Text(
        text = stringResource(
            R.string.onboarding_progress,
            state.questionIndex + 1,
            ChronotypeQuestion.entries.size,
        ),
        style = MaterialTheme.typography.labelLarge,
    )
    Text(
        text = stringResource(question.titleRes),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stringArrayResource(question.optionsRes).forEachIndexed { index, option ->
            val selected = state.selectedOption == index
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelectOption(index) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.questionIndex > 0) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
        Button(onClick = onNext, enabled = state.selectedOption != null) {
            Text(stringResource(R.string.action_next))
        }
    }
}

@Composable
private fun ConnectStep(
    result: ChronotypeResult,
    status: HealthConnectStatus?,
    onConnect: () -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        text = stringResource(result.type.titleRes),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.onboarding_baseline, result.baselineDlmo.formatted()))
    Text(stringResource(R.string.onboarding_optional))
    Text(stringResource(R.string.health_connect_title), style = MaterialTheme.typography.titleMedium)

    when (status) {
        null -> Unit
        HealthConnectStatus.UNAVAILABLE -> {
            Text(stringResource(R.string.health_connect_unavailable))
            Button(onClick = onFinish) { Text(stringResource(R.string.action_continue)) }
        }
        HealthConnectStatus.CONNECTED -> {
            Text(stringResource(R.string.health_connect_rationale))
            Button(onClick = onFinish) { Text(stringResource(R.string.action_continue)) }
        }
        HealthConnectStatus.NOT_CONNECTED -> {
            Text(stringResource(R.string.health_connect_rationale))
            Button(onClick = onConnect) { Text(stringResource(R.string.action_connect_health_connect)) }
            OutlinedButton(onClick = onFinish) { Text(stringResource(R.string.action_log_manually)) }
        }
    }
}

private fun LocalTime.formatted(): String =
    toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@get:StringRes
private val ChronotypeQuestion.titleRes: Int
    get() = when (this) {
        ChronotypeQuestion.PREFERRED_WAKE_TIME -> R.string.question_preferred_wake_time
        ChronotypeQuestion.MORNING_ALERTNESS -> R.string.question_morning_alertness
        ChronotypeQuestion.PREFERRED_BEDTIME -> R.string.question_preferred_bedtime
        ChronotypeQuestion.PEAK_TIME_OF_DAY -> R.string.question_peak_time_of_day
        ChronotypeQuestion.SELF_ASSESSMENT -> R.string.question_self_assessment
    }

@get:ArrayRes
internal val ChronotypeQuestion.optionsRes: Int
    get() = when (this) {
        ChronotypeQuestion.PREFERRED_WAKE_TIME -> R.array.options_preferred_wake_time
        ChronotypeQuestion.MORNING_ALERTNESS -> R.array.options_morning_alertness
        ChronotypeQuestion.PREFERRED_BEDTIME -> R.array.options_preferred_bedtime
        ChronotypeQuestion.PEAK_TIME_OF_DAY -> R.array.options_peak_time_of_day
        ChronotypeQuestion.SELF_ASSESSMENT -> R.array.options_self_assessment
    }

@get:StringRes
private val ChronotypeType.titleRes: Int
    get() = when (this) {
        ChronotypeType.DEFINITE_EVENING -> R.string.chronotype_definite_evening
        ChronotypeType.MODERATE_EVENING -> R.string.chronotype_moderate_evening
        ChronotypeType.INTERMEDIATE -> R.string.chronotype_intermediate
        ChronotypeType.MODERATE_MORNING -> R.string.chronotype_moderate_morning
        ChronotypeType.DEFINITE_MORNING -> R.string.chronotype_definite_morning
    }
