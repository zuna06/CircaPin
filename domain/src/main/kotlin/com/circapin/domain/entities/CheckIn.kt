package com.circapin.domain.entities

import kotlinx.datetime.LocalDate

/**
 * Morning check-in: the user rates how alert they feel on waking, and the app compares it to what
 * the model predicted. A mismatch signals that the DLMO estimate may need correction.
 *
 * @property alertness 1-5, from "still asleep" to "wide awake".
 * @property predictedAlertness the forecast's value at the check-in hour.
 * @property appliedCorrectionMinutes how much the DLMO was adjusted after this check-in (positive = later).
 */
data class CheckIn(
    val date: LocalDate,
    val alertness: Int,
    val predictedAlertness: Float,
    val appliedCorrectionMinutes: Int = 0,
)
