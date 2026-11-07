package com.circapin.domain.model

import kotlinx.datetime.LocalTime

enum class ChronotypeType {
    DEFINITE_EVENING,
    MODERATE_EVENING,
    INTERMEDIATE,
    MODERATE_MORNING,
    DEFINITE_MORNING,
}

/** Each question lists the points for its options, in display order. Wording lives in the app's string resources. */
enum class ChronotypeQuestion(val optionPoints: List<Int>) {
    PREFERRED_WAKE_TIME(listOf(5, 4, 3, 2, 1)),
    MORNING_ALERTNESS(listOf(1, 2, 3, 4)),
    PREFERRED_BEDTIME(listOf(5, 4, 3, 2, 1)),
    PEAK_TIME_OF_DAY(listOf(5, 4, 3, 2, 1)),
    SELF_ASSESSMENT(listOf(4, 3, 2, 1)),
}

data class ChronotypeResult(
    val score: Int,
    val type: ChronotypeType,
    val baselineDlmo: LocalTime,
)

/**
 * Scores the onboarding questionnaire and turns it into a day-one DLMO estimate.
 *
 * The score bands and the DLMO range are provisional. They only need to give the model a
 * reasonable starting point until sleep data and check-ins take over, and they should be
 * checked against the model validation harness in Phase 2.
 */
object ChronotypeQuestionnaire {
    val MIN_SCORE = ChronotypeQuestion.entries.sumOf { it.optionPoints.min() }
    val MAX_SCORE = ChronotypeQuestion.entries.sumOf { it.optionPoints.max() }

    private const val MINUTES_PER_DAY = 24 * 60

    // Counted from the start of the evening's calendar day, so 25:00 means 1:00 AM.
    private const val EARLIEST_DLMO_MINUTES = 20 * 60
    private const val LATEST_DLMO_MINUTES = 25 * 60

    /** @param answers the selected option index for every question. */
    fun score(answers: Map<ChronotypeQuestion, Int>): ChronotypeResult {
        val score = ChronotypeQuestion.entries.sumOf { question ->
            val option = requireNotNull(answers[question]) { "No answer for $question" }
            question.optionPoints[option]
        }
        return ChronotypeResult(score, typeFor(score), baselineDlmoFor(score))
    }

    private fun typeFor(score: Int): ChronotypeType {
        val position = (score - MIN_SCORE).toFloat() / (MAX_SCORE - MIN_SCORE)
        return when {
            position < 0.2f -> ChronotypeType.DEFINITE_EVENING
            position < 0.4f -> ChronotypeType.MODERATE_EVENING
            position < 0.6f -> ChronotypeType.INTERMEDIATE
            position < 0.8f -> ChronotypeType.MODERATE_MORNING
            else -> ChronotypeType.DEFINITE_MORNING
        }
    }

    private fun baselineDlmoFor(score: Int): LocalTime {
        val eveningness = (MAX_SCORE - score).toFloat() / (MAX_SCORE - MIN_SCORE)
        val minutes = EARLIEST_DLMO_MINUTES + (eveningness * (LATEST_DLMO_MINUTES - EARLIEST_DLMO_MINUTES)).toInt()
        val minuteOfDay = minutes % MINUTES_PER_DAY
        return LocalTime(minuteOfDay / 60, minuteOfDay % 60)
    }
}
