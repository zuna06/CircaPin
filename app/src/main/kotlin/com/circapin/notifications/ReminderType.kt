package com.circapin.notifications

enum class ReminderType(val channelId: String, val requestCodeBase: Int) {
    LIGHT("circapin_light", 100),
    CAFFEINE("circapin_caffeine", 200),
    BEDTIME("circapin_bedtime", 300),
    CHECK_IN("circapin_check_in", 400),
    MEAL("circapin_meal", 500),
    FIRST_MEAL("circapin_meal", 600),
    DIM("circapin_dim", 700),
}
