package com.circapin.domain.entities

data class ReminderPreferences(
    val lightReminder: Boolean = true,
    val caffeineReminder: Boolean = true,
    val bedtimeReminder: Boolean = true,
    val checkInReminder: Boolean = true,
    val mealReminder: Boolean = false,
    val firstMealReminder: Boolean = false,
    val dimReminder: Boolean = true,
)
