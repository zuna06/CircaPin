package com.circapin.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.domain.entities.ReminderPreferences
import com.circapin.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: ReminderPreferences = ReminderPreferences(),
    val exactAlarmsGranted: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = preferences.observeReminderPreferences().map { prefs ->
        SettingsUiState(prefs = prefs, exactAlarmsGranted = canScheduleExact())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updatePrefs(prefs: ReminderPreferences) {
        viewModelScope.launch { preferences.saveReminderPreferences(prefs) }
    }

    private fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
}
