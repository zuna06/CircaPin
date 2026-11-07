package com.circapin.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.entities.GoalStatus
import com.circapin.ui.components.formatted
import com.circapin.ui.components.formattedDate
import com.circapin.ui.components.formattedTime

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(state: ProgressUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.loading) return@Column
            if (state.goal == null) {
                Text(stringResource(R.string.progress_empty))
                return@Column
            }

            ShiftCard(state)
            Text(stringResource(R.string.body_clock_debt_and_jetlag, state.sleepDebtHours, state.socialJetlagHours))
            state.averageScore?.let { Text(stringResource(R.string.progress_average, it)) }
            Text(stringResource(R.string.progress_target_date, state.goal.targetDate.formatted()))
            TimelineCard(state)
        }
    }
}

@Composable
private fun ShiftCard(state: ProgressUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                stringResource(R.string.progress_shift_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            when (state.goalStatus) {
                GoalStatus.COMPLETED -> Text(stringResource(R.string.progress_completed), style = MaterialTheme.typography.bodyLarge)
                GoalStatus.MAINTAINED -> Text(stringResource(R.string.progress_maintained), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    if (state.totalDays > 0) {
                        Text(stringResource(R.string.progress_days_in, state.dayIndex, state.totalDays))
                        LinearProgressIndicator(
                            progress = { state.shiftProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.progress_start_label), style = MaterialTheme.typography.labelSmall)
                    Text(state.startDlmo?.formatted() ?: "—")
                }
                Column {
                    Text(stringResource(R.string.progress_current_label), style = MaterialTheme.typography.labelSmall)
                    Text(state.currentDlmo?.formatted() ?: "—")
                }
                Column {
                    Text(stringResource(R.string.progress_target_label), style = MaterialTheme.typography.labelSmall)
                    Text(state.targetDlmo?.formatted() ?: "—")
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(state: ProgressUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                stringResource(R.string.progress_timeline_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            state.snapshots.takeLast(14).forEach { snap ->
                val time = snap.estimatedDlmo.formattedTime()
                val date = snap.capturedAt.formattedDate()
                Text("$date  $time", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
