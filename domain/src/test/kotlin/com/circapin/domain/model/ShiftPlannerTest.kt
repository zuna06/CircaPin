package com.circapin.domain.model

import com.circapin.domain.entities.ScheduleGoal
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShiftPlannerTest {
    private val today = LocalDate(2026, 11, 10)

    private fun goal(nights: Int, wake: LocalTime = LocalTime(7, 0)) = ScheduleGoal(
        targetWakeTime = wake,
        targetDate = LocalDate.fromEpochDays(today.toEpochDays() + nights),
        createdAt = Instant.parse("2026-11-10T09:00:00Z"),
    )

    private fun progress(dlmo: LocalTime, nights: Int) = ShiftPlanner.progress(dlmo, goal(nights), 8f, today)

    @Test
    fun `the target DLMO is wake time minus sleep need minus two hours`() {
        assertEquals(LocalTime(21, 0), ShiftPlanner.targetDlmo(LocalTime(7, 0), 8f))
        assertEquals(LocalTime(22, 30), ShiftPlanner.targetDlmo(LocalTime(8, 0), 7.5f))
    }

    @Test
    fun `three hours over two weeks is about 15 minutes a night`() {
        val result = progress(LocalTime(0, 0), nights = 14)

        assertEquals(180, result.shiftMinutes)
        assertEquals(13, result.dailyStepMinutes)
        assertEquals(0, result.shortfallMinutes)
    }

    @Test
    fun `an advance is capped at an hour a night and the shortfall reported`() {
        val result = progress(LocalTime(2, 0), nights = 3)

        assertEquals(300, result.shiftMinutes)
        assertEquals(60, result.dailyStepMinutes)
        assertEquals(120, result.shortfallMinutes)
    }

    @Test
    fun `a delay is capped at an hour and a half a night`() {
        val result = progress(LocalTime(17, 0), nights = 2)

        assertEquals(-240, result.shiftMinutes)
        assertEquals(-90, result.dailyStepMinutes)
        assertEquals(60, result.shortfallMinutes)
    }

    @Test
    fun `a goal tomorrow moves at the maximum safe rate`() {
        val result = progress(LocalTime(0, 0), nights = 1)

        assertEquals(60, result.dailyStepMinutes)
        assertEquals(120, result.shortfallMinutes)
    }

    @Test
    fun `being on target needs only the remaining minutes`() {
        val result = progress(LocalTime(21, 10), nights = 14)

        assertEquals(10, result.dailyStepMinutes)
        assertEquals(0, result.shortfallMinutes)
    }

    @Test
    fun `after the goal date the plan keeps closing the gap`() {
        val result = progress(LocalTime(22, 0), nights = -3)

        assertEquals(0, result.nightsLeft)
        assertEquals(60, result.dailyStepMinutes)
        assertEquals(60, result.shortfallMinutes)
    }
}
