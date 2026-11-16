package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.model.SleepFixtures.UTC
import com.circapin.domain.model.SleepFixtures.night
import com.circapin.domain.model.SleepFixtures.plusDays
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepDebtCalculatorTest {
    private val start = LocalDate(2026, 10, 1)
    private val need = 8.hours

    private fun nights(lengths: List<Duration>, firstDay: Int = 0) =
        lengths.mapIndexed { i, length -> night(start.plusDays(firstDay + i), LocalTime(23, 0), length) }

    private fun debt(records: List<SleepRecord>, today: LocalDate) =
        SleepDebtCalculator.debtHours(SleepDays.from(records, UTC), need, today)

    @Test
    fun `two weeks of six-hour nights add up to 28 hours`() {
        assertEquals(28f, debt(nights(List(14) { 6.hours }), start.plusDays(13)))
    }

    @Test
    fun `long nights pay debt back`() {
        val records = nights(List(7) { 6.hours } + List(2) { 10.hours })

        assertEquals(10f, debt(records, start.plusDays(8)))
    }

    @Test
    fun `sleep cannot be banked ahead of a short night`() {
        val records = nights(List(3) { 10.hours } + listOf(6.hours))

        assertEquals(2f, debt(records, start.plusDays(3)))
    }

    @Test
    fun `unlogged days are not counted as sleepless`() {
        val records = listOf(0, 3, 9).map { night(start.plusDays(it), LocalTime(23, 0), 8.hours) }

        assertEquals(0f, debt(records, start.plusDays(9)))
    }

    @Test
    fun `nights older than the window are ignored`() {
        val old = nights(List(5) { 4.hours })
        val recent = nights(List(14) { 8.hours }, firstDay = 5)

        assertEquals(0f, debt(old + recent, start.plusDays(18)))
    }

    @Test
    fun `a nap reduces that day's debt`() {
        val records = listOf(
            night(start, LocalTime(23, 0), 6.hours),
            night(start.plusDays(1), LocalTime(14, 0), 1.hours),
        )

        assertEquals(1f, debt(records, start.plusDays(1)))
    }

    @Test
    fun `a night reported by two apps is not counted twice`() {
        val records = listOf(
            night(start, LocalTime(23, 0), 6.hours, SleepSource.HEALTH_CONNECT),
            night(start, LocalTime(23, 0), 6.hours, SleepSource.HEALTH_CONNECT),
        )

        assertEquals(2f, debt(records, start))
    }
    @Test fun `awake stage time creates a deficit even in an eight hour session`() {
        val record = night(start, LocalTime(23, 0), 8.hours).copy(qualityScore = 0.75f)
        assertEquals(2f, debt(listOf(record), start))
        assertEquals(2f, debt(listOf(record, record.copy(qualityScore = null)), start))
    }

    @Test fun `partially overlapping staged sessions are counted once and independent of order`() {
        val a = night(start, LocalTime(23, 0), 6.hours).copy(qualityScore = 0.5f)
        val b = a.copy(sleepOnset = a.sleepOnset + 2.hours, wakeTime = a.wakeTime + 2.hours, qualityScore = 0.75f)
        // Two hours at 50 percent plus six hours at 75 percent = 5.5 asleep.
        assertEquals(2.5f, debt(listOf(a, b), start))
        assertEquals(2.5f, debt(listOf(b, a, a), start))
    }

    @Test fun `staged recovery sleep repays only actual estimated surplus`() {
        val short = night(start, LocalTime(23, 0), 6.hours)
        val recovery = night(start.plusDays(1), LocalTime(23, 0), 10.hours).copy(qualityScore = 0.8f)
        assertEquals(2f, debt(listOf(short, recovery), start.plusDays(1)), 0.0001f)
    }

    @Test fun `invalid stage fractions fall back to unstaged duration`() {
        for (share in listOf(Float.NaN, -0.5f, 1.5f)) {
            assertEquals(0f, debt(listOf(night(start, LocalTime(23, 0)).copy(qualityScore = share)), start))
        }
    }

    @Test fun `sleep day changes at local noon on DST transition days`() {
        val zone = kotlinx.datetime.TimeZone.of("America/New_York")
        for (day in listOf(LocalDate(2026, 3, 8), LocalDate(2026, 11, 1))) {
            val before = kotlinx.datetime.LocalDateTime(day, LocalTime(11, 30))
            assertEquals(day.plusDays(-1), SleepDays.sleepDayOf(before.toInstant(zone), zone))
            assertEquals(day, SleepDays.sleepDayOf(kotlinx.datetime.LocalDateTime(day, LocalTime(12, 0)).toInstant(zone), zone))
        }
    }

    @Test fun `nap recovery also excludes recorded awake time`() {
        val short = night(start, LocalTime(23, 0), 6.hours)
        val nap = night(start.plusDays(1), LocalTime(14, 0), 1.hours).copy(isNap = true, qualityScore = 0.5f)
        assertEquals(1.5f, debt(listOf(short, nap), start.plusDays(1)))
    }

}
