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
        assertEquals(LocalTime(20, 0), result.baselineDlmo)
    }

    @Test
    fun `most evening answers reach the minimum score and a DLMO past midnight`() {
        val result = ChronotypeQuestionnaire.score(mostEvening)

        assertEquals(ChronotypeQuestionnaire.MIN_SCORE, result.score)
        assertEquals(ChronotypeType.DEFINITE_EVENING, result.type)
        assertEquals(LocalTime(1, 0), result.baselineDlmo)
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
