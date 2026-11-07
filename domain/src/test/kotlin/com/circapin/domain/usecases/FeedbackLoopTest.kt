package com.circapin.domain.usecases

import com.circapin.domain.entities.*
import com.circapin.domain.repository.*
import com.circapin.domain.usecases.PlanFixtures.TUESDAY
import com.circapin.domain.usecases.PlanFixtures.UTC
import com.circapin.domain.usecases.PlanFixtures.NOW
import com.circapin.domain.usecases.PlanFixtures.model
import com.circapin.domain.usecases.PlanFixtures.profile
import com.circapin.domain.usecases.PlanFixtures.goal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeedbackLoopTest {
    private class Checks : CheckInRepository {
        val rows = MutableStateFlow<List<CheckIn>>(emptyList())
        override fun observeAll() = rows
        override suspend fun get(date: LocalDate) = rows.value.find { it.date == date }
        override suspend fun save(checkIn: CheckIn) { rows.value = rows.value.filterNot { it.date == checkIn.date } + checkIn }
    }
    private class Models : ModelRepository {
        val state = MutableStateFlow<ModelState?>(null)
        var saves = 0
        override fun observeState() = state
        override suspend fun save(state: ModelState) { this.state.value = state; saves++ }
    }
    private val checks = Checks()
    private val models = Models()
    private val process = ProcessCheckInUseCase(checks, models)
    private val base = model(LocalTime(21, 0))
    private fun forecast(prediction: Float) = EnergyForecast(TUESDAY, List(24) { prediction })
    private fun yesterday(alertness: Int, predicted: Float) = CheckIn(LocalDate.fromEpochDays(TUESDAY.toEpochDays() - 1), alertness, predicted, 0)

    @Test fun `first mismatch is recorded without a correction`() = runTest {
        assertEquals(0, process(1, TUESDAY, forecast(1f), base, 7).appliedCorrectionMinutes)
        assertEquals(0, models.saves)
    }
    @Test fun `agreement needs no correction`() = runTest {
        checks.save(yesterday(1, 1f))
        assertEquals(0, process(3, TUESDAY, forecast(0.5f), base, 7).appliedCorrectionMinutes)
    }
    @Test fun `opposite mismatches do not correct`() = runTest {
        checks.save(yesterday(5, 0f))
        assertEquals(0, process(1, TUESDAY, forecast(1f), base, 7).appliedCorrectionMinutes)
    }
    @Test fun `one point tired shifts later fifteen minutes`() = runTest {
        checks.save(yesterday(2, 0.5f))
        assertEquals(15, process(2, TUESDAY, forecast(0.5f), base, 7).appliedCorrectionMinutes)
        assertEquals(base.estimatedDlmo + 15.minutes, models.state.value?.estimatedDlmo)
    }
    @Test fun `two points tired shifts later thirty minutes`() = runTest {
        checks.save(yesterday(1, 0.5f))
        assertEquals(30, process(1, TUESDAY, forecast(0.5f), base, 7).appliedCorrectionMinutes)
    }
    @Test fun `two points alert shifts earlier thirty minutes`() = runTest {
        checks.save(yesterday(5, 0.5f))
        assertEquals(-30, process(5, TUESDAY, forecast(0.5f), base, 7).appliedCorrectionMinutes)
    }
    @Test fun `a missing day breaks the streak`() = runTest {
        checks.save(CheckIn(LocalDate.fromEpochDays(TUESDAY.toEpochDays() - 2), 1, 1f, 0))
        assertEquals(0, process(1, TUESDAY, forecast(1f), base, 7).appliedCorrectionMinutes)
    }
    @Test fun `repeating a check-in cannot apply the correction twice`() = runTest {
        checks.save(yesterday(1, 1f))
        val first = process(1, TUESDAY, forecast(1f), base, 7)
        assertEquals(first, process(5, TUESDAY, forecast(0f), base, 7))
        assertEquals(1, models.saves)
    }
    @Test fun `invalid rating is rejected`() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { process(0, TUESDAY, forecast(1f), base, 7) } }
    }
    @Test fun `sync retains feedback without compounding it`() = runTest {
        val clock = object : Clock { override fun now() = NOW }
        val update = UpdateModelUseCase(models, clock, checks)
        update(emptyList(), profile(), UTC)
        val original = models.state.value!!
        checks.save(CheckIn(TUESDAY, 1, 1f, 30))
        repeat(3) { update(emptyList(), profile(), UTC) }
        assertEquals(original.estimatedDlmo + 30.minutes, models.state.value?.estimatedDlmo)
    }
    @Test fun `synthetic feedback approaches a known phase without oscillation`() = runTest {
        var estimate = base
        val target = base.estimatedDlmo + 120.minutes
        val errors = mutableListOf<Long>()
        repeat(10) { day ->
            val error = (target - estimate.estimatedDlmo).inWholeMinutes
            val predicted = (0.5f + error / 120f).coerceIn(0f, 1f)
            process(3, LocalDate.fromEpochDays(TUESDAY.toEpochDays() + day), forecast(predicted), estimate, 7)
            estimate = models.state.value ?: estimate
            errors += (target - estimate.estimatedDlmo).inWholeMinutes
        }
        assertTrue(errors.zipWithNext().all { (a, b) -> b in 0..a })
        assertTrue(errors.last() <= 30, "synthetic residual: ${errors.last()}")
    }
}
