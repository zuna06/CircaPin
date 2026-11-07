package com.circapin.notifications

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.ReminderPreferences
import kotlin.time.Instant

interface PlanReminders {
    fun cancelCheckInToday() {}
    fun schedule(plan: DailyPlan, eatingWindow: EatingWindow?, prefs: ReminderPreferences, wakeTime: Instant, checkedIn: Boolean = false)
}
