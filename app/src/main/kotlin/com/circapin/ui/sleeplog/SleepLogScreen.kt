package com.circapin.ui.sleeplog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.ui.components.rememberHealthConnectPermissionRequest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val MINUTES_PER_HOUR = 60

@Composable
fun SleepLogRoute(onAddEntry: (isNap: Boolean) -> Unit, viewModel: SleepLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberHealthConnectPermissionRequest(viewModel::sync)

    SleepLogScreen(
        state = state,
        onSync = viewModel::sync,
        onConnect = requestPermission,
        onAddEntry = onAddEntry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepLogScreen(
    state: SleepLogUiState,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onAddEntry: (isNap: Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_log_title)) },
                actions = {
                    TextButton(onClick = onSync, enabled = state.syncStatus != SyncStatus.SYNCING) {
                        Text(stringResource(R.string.action_sync))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(onClick = { onAddEntry(false) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_log_sleep))
                }
                OutlinedButton(onClick = { onAddEntry(true) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_log_nap))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SyncBanner(state.syncStatus, onConnect)
            state.model?.let { BodyClockCard(it) }
            val records = state.records
            when {
                records == null -> Unit
                records.isEmpty() -> Text(
                    text = stringResource(R.string.sleep_log_empty),
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(records) { record ->
                        SleepRecordRow(record)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncBanner(status: SyncStatus, onConnect: () -> Unit) {
    val message = when (status) {
        SyncStatus.SYNCED -> return
        SyncStatus.SYNCING -> R.string.sync_in_progress
        SyncStatus.NOT_CONNECTED -> R.string.sync_not_connected
        SyncStatus.UNAVAILABLE -> R.string.sync_unavailable
        SyncStatus.FAILED -> R.string.sync_failed
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (status == SyncStatus.NOT_CONNECTED) {
                TextButton(onClick = onConnect) { Text(stringResource(R.string.action_connect)) }
            }
        }
    }
}

@Composable
private fun BodyClockCard(model: ModelState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(stringResource(R.string.body_clock_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.body_clock_dlmo, model.estimatedDlmo.formattedTime()))
            Text(stringResource(R.string.body_clock_debt_and_jetlag, model.sleepDebtHours, model.socialJetlagHours))
            Text(
                text = if (model.confidence == 0f) {
                    stringResource(R.string.body_clock_questionnaire_only)
                } else {
                    stringResource(R.string.body_clock_confidence, (model.confidence * 100).roundToInt())
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SleepRecordRow(record: SleepRecord) {
    val details = buildList {
        add(stringResource(if (record.isNap) R.string.sleep_kind_nap else R.string.sleep_kind_night))
        add(
            stringResource(
                when (record.source) {
                    SleepSource.HEALTH_CONNECT -> R.string.sleep_source_health_connect
                    SleepSource.MANUAL -> R.string.sleep_source_manual
                },
            ),
        )
        record.qualityScore?.let { add(stringResource(R.string.sleep_quality, (it * 100).roundToInt())) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(record.wakeTime.formattedDate(), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                R.string.sleep_time_range,
                record.sleepOnset.formattedTime(),
                record.wakeTime.formattedTime(),
            ),
        )
        Text(
            stringResource(
                R.string.sleep_duration,
                record.durationMinutes / MINUTES_PER_HOUR,
                record.durationMinutes % MINUTES_PER_HOUR,
            ),
        )
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

private fun Instant.formattedDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(toJavaInstant())

private fun Instant.formattedTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(toJavaInstant())
