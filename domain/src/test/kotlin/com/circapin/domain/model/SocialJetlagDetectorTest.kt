package com.circapin.domain.model

import com.circapin.domain.model.SleepFixtures.UTC
import com.circapin.domain.model.SleepFixtures.night
import com.circapin.domain.model.SleepFixtures.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SocialJetlagDetectorTest {
    private val monday = LocalDate(2026, 11, 2)

    private fun twoWeeks(school: LocalTime, free: LocalTime) = (0 until 14).map { day ->
        val date = monday.plusDays(day)
        night(date, if (SleepDay(date, null, kotlin.time.Duration.ZERO).isFreeNight) free else school)
    }

    private fun jetlag(records: List<com.circapin.domain.entities.SleepRecord>) =
        SocialJetlagDetector.hours(SleepDays.from(records, UTC), monday.plusDays(14), UTC)

    @Test
    fun `the outline example is three hours`() {
        assertEquals(3f, jetlag(twoWeeks(school = LocalTime(0, 0), free = LocalTime(3, 0))), 0.01f)
    }

    @Test
    fun `nights either side of midnight are compared correctly`() {
        assertEquals(2f, jetlag(twoWeeks(school = LocalTime(23, 30), free = LocalTime(1, 30))), 0.01f)
    }

    @Test
    fun `earlier weekends give a negative value`() {
        assertEquals(-1f, jetlag(twoWeeks(school = LocalTime(0, 0), free = LocalTime(23, 0))), 0.01f)
    }

    @Test
    fun `without both kinds of night there is no estimate`() {
        val schoolNightsOnly = (0 until 5).map { night(monday.plusDays(it), LocalTime(0, 0)) }

        assertEquals(0f, jetlag(schoolNightsOnly))
        assertEquals(0f, jetlag(emptyList()))
    }
}
