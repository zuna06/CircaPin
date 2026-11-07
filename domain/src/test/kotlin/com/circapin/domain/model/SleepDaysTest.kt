package com.circapin.domain.model

import com.circapin.domain.entities.SleepSource
import com.circapin.domain.model.SleepFixtures.UTC
import com.circapin.domain.model.SleepFixtures.night
import com.circapin.domain.model.SleepFixtures.plusDays
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepDaysTest {
    private val thursday = LocalDate(2026, 11, 5)

    @Test
    fun `a night past midnight belongs to the evening it started`() {
        val day = SleepDays.from(listOf(night(thursday, LocalTime(1, 30))), UTC).single()

        assertEquals(thursday, day.date)
        assertEquals(8.hours, day.totalAsleep)
    }

    @Test
    fun `the same night from two apps counts once`() {
        val records = listOf(
            night(thursday, LocalTime(23, 0), source = SleepSource.HEALTH_CONNECT),
            night(thursday, LocalTime(23, 15), source = SleepSource.HEALTH_CONNECT),
        )

        val day = SleepDays.from(records, UTC).single()

        assertEquals(8.hours + 15.minutes, day.totalAsleep)
        assertEquals(records[0].sleepOnset, day.main?.onset)
    }

    @Test
    fun `fragments with a short awakening make one bout`() {
        val records = listOf(
            night(thursday, LocalTime(23, 0), length = 3.hours),
            night(thursday, LocalTime(2, 30), length = 4.hours),
        )

        val day = SleepDays.from(records, UTC).single()

        assertEquals(7.hours, day.main?.asleep)
        assertEquals(records[0].sleepOnset, day.main?.onset)
        assertEquals(records[1].wakeTime, day.main?.wake)
    }

    @Test
    fun `a morning nap after the night stays on the same sleep day`() {
        val records = listOf(
            night(thursday, LocalTime(0, 30)),
            night(thursday, LocalTime(10, 0), length = 40.minutes),
        )

        val day = SleepDays.from(records, UTC).single()

        assertEquals(8.hours + 40.minutes, day.totalAsleep)
        assertEquals(8.hours, day.main?.asleep)
    }

    @Test
    fun `an afternoon nap belongs to the next sleep day`() {
        val records = listOf(night(thursday, LocalTime(23, 0)), night(thursday.plusDays(1), LocalTime(15, 0), 30.minutes))

        assertEquals(listOf(thursday, thursday.plusDays(1)), SleepDays.from(records, UTC).map { it.date })
    }

    @Test
    fun `a short night can time the body clock but a lone nap cannot`() {
        val shortNight = SleepDays.from(listOf(night(thursday, LocalTime(4, 0), length = 2.hours + 30.minutes)), UTC)
        val loneNap = SleepDays.from(listOf(night(thursday, LocalTime(15, 0), length = 40.minutes)), UTC)

        assertEquals(2.hours + 30.minutes, shortNight.single().main?.asleep)
        assertNull(loneNap.single().main)
        assertEquals(40.minutes, loneNap.single().totalAsleep)
    }

    @Test
    fun `Friday and Saturday nights are free nights`() {
        val monday = LocalDate(2026, 11, 2)
        val free = (0 until 7).map { monday.plusDays(it) }.filter { SleepDay(it, null, 0.hours).isFreeNight }

        assertEquals(listOf(LocalDate(2026, 11, 6), LocalDate(2026, 11, 7)), free)
        assertTrue(SleepDay(LocalDate(2026, 11, 6), null, 0.hours).isFreeNight)
        assertFalse(SleepDay(LocalDate(2026, 11, 8), null, 0.hours).isFreeNight)
    }
}
