package com.circapin.data.repository

import android.content.Context
import com.circapin.domain.entities.ReminderPreferences
import com.circapin.domain.repository.PreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PreferencesRepositoryImpl(context: Context) : PreferencesRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun observeReminderPreferences(): Flow<ReminderPreferences> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun saveReminderPreferences(prefs: ReminderPreferences) {
        this.prefs.edit()
            .putBoolean(KEY_LIGHT, prefs.lightReminder)
            .putBoolean(KEY_CAFFEINE, prefs.caffeineReminder)
            .putBoolean(KEY_BEDTIME, prefs.bedtimeReminder)
            .putBoolean(KEY_CHECK_IN, prefs.checkInReminder)
            .putBoolean(KEY_MEAL, prefs.mealReminder)
            .apply()
    }

    private fun read() = ReminderPreferences(
        lightReminder = prefs.getBoolean(KEY_LIGHT, true),
        caffeineReminder = prefs.getBoolean(KEY_CAFFEINE, true),
        bedtimeReminder = prefs.getBoolean(KEY_BEDTIME, true),
        checkInReminder = prefs.getBoolean(KEY_CHECK_IN, true),
        mealReminder = prefs.getBoolean(KEY_MEAL, false),
    )

    private companion object {
        const val PREFS_NAME = "circapin_prefs"
        const val KEY_LIGHT = "reminder_light"
        const val KEY_CAFFEINE = "reminder_caffeine"
        const val KEY_BEDTIME = "reminder_bedtime"
        const val KEY_CHECK_IN = "reminder_check_in"
        const val KEY_MEAL = "reminder_meal"
    }
}
