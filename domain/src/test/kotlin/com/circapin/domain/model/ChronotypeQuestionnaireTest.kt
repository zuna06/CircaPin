package com.circapin.domain.model

import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChronotypeQuestionnaireTest {
    private fun answersWhere(pick: (List<Int>) -> Int) =
        ChronotypeQuestion.entries.associateWith { question -> question.optionPoints.indexOf(pick(question.optionPoints)) }

    private val mostMorning = answersWhere { it.max() }
    private val mostEvening = answersWhere { it.min() }

    @Test
    fun `most morning answers reach the maximum score and earliest DLMO`() {
        val result = ChronotypeQuestionnaire.score(mostMorning)

        assertEquals(ChronotypeQuestionnaire.MAX_SCORE, result.score)
        assertEquals(ChronotypeType.DEFINITE_MORNING, result.type)
        assertEquals(LocalTime(18, 30), result.baselineDlmo)
    }

    @Test
    fun `most evening answers reach the minimum score and a DLMO past midnight`() {
        val result = ChronotypeQuestionnaire.score(mostEvening)

        assertEquals(ChronotypeQuestionnaire.MIN_SCORE, result.score)
        assertEquals(ChronotypeType.DEFINITE_EVENING, result.type)
        assertEquals(LocalTime(0, 30), result.baselineDlmo)
    }

    /**
     * Someone who answers consistently should get a DLMO about two hours before the bedtime they chose, the
     * outline's rule. Bedtimes are the middle of each answer's range, measured from the evening's midnight.
     */
    @Test
    fun `consistent answers land within 45 minutes of two hours before the chosen bedtime`() {
        val bedtimeMinutes = listOf(20 * 60 + 30, 21 * 60 + 38, 23 * 60 + 30, 25 * 60 + 23, 26 * 60 + 30)

        bedtimeMinutes.forEachIndexed { level, bedtime ->
            val eveningness = level / 4.0
            val answers = ChronotypeQuestion.entries.associateWith { question ->
                val morningFirst = question.optionPoints.indices.sortedByDescending { question.optionPoints[it] }
                morningFirst[kotlin.math.round(eveningness * (morningFirst.size - 1)).toInt()]
            }
            val dlmo = ChronotypeQuestionnaire.score(answers).baselineDlmo
            val dlmoMinutes = (dlmo.hour * 60 + dlmo.minute).let { if (it < 12 * 60) it + 24 * 60 else it }

            assertTrue(
                kotlin.math.abs(dlmoMinutes - (bedtime - 120)) <= 45,
                "level $level: DLMO $dlmo, bedtime ${bedtime / 60}:${bedtime % 60}",
            )
        }
    }

    @Test
    fun `a higher score never gives a later DLMO`() {
        val dlmoMinutesByScore = (ChronotypeQuestionnaire.MIN_SCORE..ChronotypeQuestionnaire.MAX_SCORE).map { target ->
            var remaining = target - ChronotypeQuestionnaire.MIN_SCORE
            val answers = ChronotypeQuestion.entries.associateWith { question ->
                val ascending = question.optionPoints.sorted()
                val step = minOf(remaining, ascending.size - 1)
                remaining -= step
                question.optionPoints.indexOf(ascending[step])
            }
            val dlmo = ChronotypeQuestionnaire.score(answers).baselineDlmo
            val minutes = dlmo.hour * 60 + dlmo.minute
            if (minutes < 12 * 60) minutes + 24 * 60 else minutes
        }

        assertTrue(dlmoMinutesByScore.zipWithNext().all { (lowerScore, higherScore) -> lowerScore >= higherScore })
    }

    @Test
    fun `a missing answer is rejected`() {
        assertThrows<IllegalArgumentException> {
            ChronotypeQuestionnaire.score(mostMorning - ChronotypeQuestion.SELF_ASSESSMENT)
        }
    }
}
