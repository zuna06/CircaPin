package com.circapin.ui.sleeplog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.usecases.SleepEntryProblem
import com.circapin.ui.components.DatePickerPopup
import com.circapin.ui.components.TimePickerPopup
import com.circapin.ui.components.formatted
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
fun SleepEntryRoute(onDone: () -> Unit, viewModel: SleepEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    SleepEntryScreen(
        state = state,
        onOnsetDate = viewModel::setOnsetDate,
        onOnsetTime = viewModel::setOnsetTime,
        onWakeDate = viewModel::setWakeDate,
        onWakeTime = viewModel::setWakeTime,
        onSave = viewModel::save,
        onCancel = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepEntryScreen(
    state: SleepEntryUiState,
    onOnsetDate: (LocalDate) -> Unit,
    onOnsetTime: (LocalTime) -> Unit,
    onWakeDate: (LocalDate) -> Unit,
    onWakeTime: (LocalTime) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNap) R.string.action_log_nap else R.string.action_log_sleep)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DateTimeField(R.string.sleep_entry_onset, state.onsetDate, state.onsetTime, onOnsetDate, onOnsetTime)
            DateTimeField(R.string.sleep_entry_wake, state.wakeDate, state.wakeTime, onWakeDate, onWakeTime)
            state.problem?.let { problem ->
                Text(
                    text = stringResource(problem.messageRes(state.isNap)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun DateTimeField(
    labelRes: Int,
    date: LocalDate,
    time: LocalTime,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val label = stringResource(labelRes)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                Text(date.formatted())
            }
            OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.weight(1f)) {
                Text(time.formatted())
            }
        }
    }

    if (pickingDate) {
        DatePickerPopup(date, onDismiss = { pickingDate = false }, onConfirm = onDate)
    }
    if (pickingTime) {
        TimePickerPopup(label, time, onDismiss = { pickingTime = false }, onConfirm = onTime)
    }
}

private fun SleepEntryProblem.messageRes(isNap: Boolean): Int = when (this) {
    SleepEntryProblem.WAKE_NOT_AFTER_ONSET -> R.string.sleep_entry_error_order
    SleepEntryProblem.IN_FUTURE -> R.string.sleep_entry_error_future
    SleepEntryProblem.TOO_LONG ->
        if (isNap) R.string.sleep_entry_error_too_long_nap else R.string.sleep_entry_error_too_long
    SleepEntryProblem.OVERLAPS_EXISTING -> R.string.sleep_entry_error_overlap
}
