package com.circapin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.circapin.data.UserDataActions
import com.circapin.domain.entities.ReminderPreferences
import com.circapin.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val prefs: ReminderPreferences = ReminderPreferences(),
    val exactAlarmsGranted: Boolean = false,
    val permissions: PermissionState = PermissionState(),
    val busy: Boolean = false,
    val error: Boolean = false,
    val exported: File? = null,
    val resetDone: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val permissions: PermissionReader,
    private val data: UserDataActions,
) : ViewModel() {
    private val actions = MutableStateFlow(SettingsUiState())
    val state = combine(preferences.observeReminderPreferences(), actions) { prefs, action -> action.copy(prefs = prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refreshPermissions() { viewModelScope.launch {
        try { val p = permissions.read(); actions.update { it.copy(permissions = p, exactAlarmsGranted = p.exact) } }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { actions.update { it.copy(error = true) } }
    } }
    fun updatePrefs(prefs: ReminderPreferences) { viewModelScope.launch { preferences.saveReminderPreferences(prefs) } }
    fun export(csv: Boolean) = action { val file = data.export(csv); actions.update { it.copy(exported = file) } }
    fun consumeExport() { actions.update { it.copy(exported = null) } }
    fun deleteAll() = action { data.deleteAll(); actions.update { it.copy(resetDone = true) } }
    private fun action(block: suspend () -> Unit) {
        if (actions.value.busy) return
        actions.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            try { block() }
            catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (_: Exception) { actions.update { it.copy(error = true) } }
            finally { actions.update { it.copy(busy = false) } }
        }
    }
}
