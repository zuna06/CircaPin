package com.circapin.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.entities.ReminderPreferences

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onPrefsChange = viewModel::updatePrefs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onPrefsChange: (ReminderPreferences) -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_reminders_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp),
            )
            SwitchRow(
                label = stringResource(R.string.settings_light_reminder),
                checked = state.prefs.lightReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(lightReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_caffeine_reminder),
                checked = state.prefs.caffeineReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(caffeineReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_bedtime_reminder),
                checked = state.prefs.bedtimeReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(bedtimeReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_checkin_reminder),
                checked = state.prefs.checkInReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(checkInReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_meal_reminder),
                checked = state.prefs.mealReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(mealReminder = it)) },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_exact_alarms))
                        Text(
                            text = stringResource(R.string.settings_exact_alarms_summary),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (state.exactAlarmsGranted) R.string.settings_exact_alarms_granted
                            else R.string.settings_exact_alarms_denied,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.exactAlarmsGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
