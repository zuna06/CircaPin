package com.circapin.notifications

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.ReminderPreferences
import kotlin.time.Instant

interface PlanReminders {
    fun cancelCheckInToday() {}
    /** Today's wake is derived from eatingWindow; nextWakeTime is tomorrow's fallback after tonight's sleep. */
    fun schedule(plan: DailyPlan, eatingWindow: EatingWindow?, prefs: ReminderPreferences, nextWakeTime: Instant, checkedIn: Boolean = false)
}
