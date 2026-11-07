package com.circapin.domain.entities

import kotlin.time.Instant

/**
 * The circadian model's current estimate.
 *
 * @property estimatedDlmo melatonin onset on the current sleep day: tonight's, or last night's before noon.
 * @property socialJetlagHours weekend minus weekday sleep midpoint. Zero until both kinds of night are known.
 * @property confidence 0 with no sleep data, rising to 1 after a week of nights.
 */
data class ModelState(
    val estimatedDlmo: Instant,
    val sleepDebtHours: Float,
    val socialJetlagHours: Float,
    val confidence: Float,
    val lastUpdated: Instant,
)
