package com.circapin.domain.repository

import com.circapin.domain.entities.ReminderPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observeReminderPreferences(): Flow<ReminderPreferences>

    suspend fun saveReminderPreferences(prefs: ReminderPreferences)
}
