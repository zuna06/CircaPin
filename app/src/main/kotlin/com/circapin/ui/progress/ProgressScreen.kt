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
import com.circapin.ui.components.ScreenColumn
import androidx.compose.ui.platform.LocalDensity
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
        ScreenColumn(Modifier.padding(padding)) {
            if (state.loading) return@ScreenColumn
            if (state.goal == null) {
                Text(stringResource(R.string.progress_empty))
                return@ScreenColumn
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

            if (LocalDensity.current.fontScale <= 1.3f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhaseValue(stringResource(R.string.progress_start_label), state.startDlmo?.formatted(), Modifier.weight(1f))
                    PhaseValue(stringResource(R.string.progress_current_label), state.currentDlmo?.formatted(), Modifier.weight(1f))
                    PhaseValue(stringResource(R.string.progress_target_label), state.targetDlmo?.formatted(), Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhaseValue(stringResource(R.string.progress_start_label), state.startDlmo?.formatted())
                    PhaseValue(stringResource(R.string.progress_current_label), state.currentDlmo?.formatted())
                    PhaseValue(stringResource(R.string.progress_target_label), state.targetDlmo?.formatted())
                }
            }
        }
    }
}

@Composable
private fun PhaseValue(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium)
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
