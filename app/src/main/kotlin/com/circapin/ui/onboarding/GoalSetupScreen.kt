package com.circapin.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.circapin.ui.components.ScreenColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.entities.GoalReason
import com.circapin.domain.model.GoalProgress
import com.circapin.domain.model.ShiftPlanner
import com.circapin.ui.components.DatePickerPopup
import com.circapin.ui.components.TimePickerPopup
import com.circapin.ui.components.durationText
import com.circapin.ui.components.formatted
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** @param onCancel null during onboarding, where a goal is part of setup. */
@Composable
fun GoalSetupRoute(onSaved: () -> Unit, onCancel: (() -> Unit)?, viewModel: GoalSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.form.saved) {
        if (state.form.saved) onSaved()
    }

    GoalSetupScreen(
        state = state,
        onWakeTime = viewModel::setWakeTime,
        onDate = viewModel::setDate,
        onReason = viewModel::toggleReason,
        onSave = viewModel::save,
        onCancel = onCancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalSetupScreen(
    state: GoalSetupUiState,
    onWakeTime: (LocalTime) -> Unit,
    onDate: (LocalDate) -> Unit,
    onReason: (GoalReason) -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val wakeLabel = stringResource(R.string.goal_wake_label)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_title)) },
                navigationIcon = {
                    onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_cancel)) } }
                },
            )
        },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding), maxWidth = 600.dp) {
            Text(stringResource(R.string.goal_intro), style = MaterialTheme.typography.bodyLarge)

            Text(wakeLabel, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.form.wakeTime.formatted(), style = MaterialTheme.typography.headlineSmall)
            }

            Text(stringResource(R.string.goal_date_label), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.form.date.formatted())
            }
            if (state.form.dateTooEarly) {
                Text(
                    text = stringResource(R.string.goal_date_too_early),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            Text(stringResource(R.string.goal_reason_label), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalReason.entries.forEach { reason ->
                    FilterChip(
                        selected = state.form.reason == reason,
                        onClick = { onReason(reason) },
                        label = { Text(stringResource(reason.labelRes)) },
                    )
                }
            }

            state.preview?.let { Preview(it) }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save_goal))
            }
        }
    }

    if (pickingTime) {
        TimePickerPopup(wakeLabel, state.form.wakeTime, onDismiss = { pickingTime = false }, onConfirm = onWakeTime)
    }
    if (pickingDate) {
        DatePickerPopup(state.form.date, onDismiss = { pickingDate = false }, onConfirm = onDate, earliest = state.earliestDate)
    }
}

@Composable
private fun Preview(progress: GoalProgress) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        val nights = pluralStringResource(R.plurals.nights, progress.nightsLeft, progress.nightsLeft)
        val step = abs(progress.dailyStepMinutes)
        Text(
            when {
                abs(progress.shiftMinutes) <= ShiftPlanner.ON_TARGET_MINUTES -> stringResource(R.string.goal_preview_on_target)
                progress.shiftMinutes > 0 ->
                    stringResource(R.string.goal_preview_earlier, durationText(progress.shiftMinutes), step, nights)
                else -> stringResource(R.string.goal_preview_later, durationText(progress.shiftMinutes), step, nights)
            },
        )
        if (progress.shortfallMinutes > 0) {
            Text(
                text = stringResource(R.string.goal_preview_short, durationText(progress.shortfallMinutes)),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val GoalReason.labelRes: Int
    get() = when (this) {
        GoalReason.INTERNSHIP -> R.string.goal_reason_internship
        GoalReason.FINALS -> R.string.goal_reason_finals
        GoalReason.CLASS_SCHEDULE -> R.string.goal_reason_class_schedule
        GoalReason.TRAVEL -> R.string.goal_reason_travel
        GoalReason.OTHER -> R.string.goal_reason_other
    }
