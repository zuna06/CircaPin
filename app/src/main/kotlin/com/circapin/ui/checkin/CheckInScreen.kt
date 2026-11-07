package com.circapin.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R

private val alertnessLabels = listOf(
    R.string.checkin_level_1,
    R.string.checkin_level_2,
    R.string.checkin_level_3,
    R.string.checkin_level_4,
    R.string.checkin_level_5,
)

@Composable
fun CheckInRoute(onDone: () -> Unit, viewModel: CheckInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckInScreen(state = state, onSubmit = viewModel::submit, onDone = onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(state: CheckInUiState, onSubmit: (Int) -> Unit, onDone: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.checkin_title)) }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.loading) return@Column

            if (state.alreadyCheckedIn) {
                CheckedInCard(state)
            } else if (!state.available) {
                Text(stringResource(R.string.checkin_unavailable))
            } else {
                Text(
                    text = stringResource(R.string.checkin_question),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alertnessLabels.forEachIndexed { index, labelRes ->
                        FilledTonalButton(
                            onClick = { onSubmit(index + 1) },
                            enabled = !state.submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                }
            }
            if (state.error) Text(stringResource(R.string.operation_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun CheckedInCard(state: CheckInUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.checkin_done),
                style = MaterialTheme.typography.titleMedium,
            )
            val checkIn = state.todayCheckIn
            if (checkIn != null && checkIn.appliedCorrectionMinutes != 0) {
                Text(stringResource(R.string.checkin_correction, checkIn.appliedCorrectionMinutes))
            } else {
                Text(stringResource(R.string.checkin_no_correction))
            }
        }
    }
}
