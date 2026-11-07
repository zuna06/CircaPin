package com.circapin.ui.settings

import android.content.Intent
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.core.content.FileProvider
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
fun SettingsRoute(onReset: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.export_share)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions() }
        lifecycle.addObserver(observer)
        viewModel.refreshPermissions()
        onDispose { lifecycle.removeObserver(observer) }
    }
    val background = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.refreshPermissions() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }
    LaunchedEffect(state.exported) {
        state.exported?.let { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension == "zip") "application/zip" else "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("CircaPin export", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, shareTitle))
            viewModel.consumeExport()
        }
    }
    LaunchedEffect(state.resetDone) { if (state.resetDone) onReset() }
    SettingsScreen(state, viewModel::updatePrefs,
        onBackground = { background.launch(com.circapin.data.healthconnect.HealthConnectSleepSource.PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) },
        onNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && !state.permissions.notifications) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, onExport = viewModel::export, onDeleteAll = { viewModel.deleteAll() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onPrefsChange: (ReminderPreferences) -> Unit,
    onBackground: () -> Unit = {}, onNotifications: () -> Unit = {}, onExport: (Boolean) -> Unit = {}, onDeleteAll: () -> Unit = {}) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_all)) },
        text = { Text(stringResource(R.string.delete_all_warning)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAll() }) { Text(stringResource(R.string.delete_all)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } })
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
                enabled = !state.busy,
                checked = state.prefs.lightReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(lightReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_caffeine_reminder),
                enabled = !state.busy,
                checked = state.prefs.caffeineReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(caffeineReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_bedtime_reminder),
                enabled = !state.busy,
                checked = state.prefs.bedtimeReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(bedtimeReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_checkin_reminder),
                enabled = !state.busy,
                checked = state.prefs.checkInReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(checkInReminder = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.settings_meal_reminder),
                enabled = !state.busy,
                checked = state.prefs.mealReminder,
                onCheckedChange = { onPrefsChange(state.prefs.copy(mealReminder = it)) },
            )

            SwitchRow(stringResource(R.string.settings_first_meal), state.prefs.firstMealReminder, enabled = !state.busy) { onPrefsChange(state.prefs.copy(firstMealReminder = it)) }
            SwitchRow(stringResource(R.string.settings_dim), state.prefs.dimReminder, enabled = !state.busy) { onPrefsChange(state.prefs.copy(dimReminder = it)) }
            Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permissions_explanation))
            Button(onClick = onNotifications) { Text(stringResource(if (state.permissions.notifications) R.string.notifications_manage else R.string.notifications_enable)) }
            Text(stringResource(R.string.settings_background_sync_summary))
            Button(onClick = onBackground, enabled = state.permissions.backgroundAvailable) {
                Text(stringResource(if (state.permissions.background) R.string.settings_background_sync_granted else if (state.permissions.backgroundAvailable) R.string.background_enable else R.string.background_unavailable))
            }
            Text(stringResource(R.string.usage_explanation))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))) }) {
                Text(stringResource(if (state.permissions.usage) R.string.usage_manage else R.string.usage_enable))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
            Text(stringResource(R.string.data_controls), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.export_explanation))
            Button(onClick = { onExport(false) }, enabled = !state.busy) { Text(stringResource(R.string.export_json)) }
            Button(onClick = { onExport(true) }, enabled = !state.busy) { Text(stringResource(R.string.export_csv)) }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !state.busy) { Text(stringResource(R.string.delete_all)) }
            if (state.busy) CircularProgressIndicator()
            if (state.error) Text(stringResource(R.string.operation_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
