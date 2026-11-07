package com.circapin.ui.sleeplog

import androidx.lifecycle.SavedStateHandle
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.usecases.LogSleepUseCase
import com.circapin.domain.usecases.SleepEntryProblem
import com.circapin.ui.FakeSleepRepository
import com.circapin.ui.MainDispatcherExtension
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SleepEntryViewModelTest {
    private val now = Instant.parse("2026-11-06T14:30:45Z")
    private val clock = object : Clock {
        override fun now() = this@SleepEntryViewModelTest.now
    }
    private val zone = TimeZone.of("America/New_York")
    private val repository = FakeSleepRepository()

    private fun viewModel(isNap: Boolean) = SleepEntryViewModel(
        SavedStateHandle(mapOf(SLEEP_ENTRY_IS_NAP_ARG to isNap)),
        LogSleepUseCase(repository, clock),
        clock,
        zone,
    )

    @Test
    fun `a night defaults to eight hours ending now, in local time`() {
        val state = viewModel(isNap = false).state.value

        assertEquals(LocalDate(2026, 11, 6), state.wakeDate)
        assertEquals(LocalTime(9, 30), state.wakeTime)
        assertEquals(LocalDate(2026, 11, 6), state.onsetDate)
        assertEquals(LocalTime(1, 30), state.onsetTime)
    }

    @Test
    fun `a nap defaults to thirty minutes ending now`() {
        val state = viewModel(isNap = true).state.value

        assertTrue(state.isNap)
        assertEquals(LocalTime(9, 0), state.onsetTime)
    }

    @Test
    fun `saving converts local times to the correct instants`() {
        val viewModel = viewModel(isNap = false)
        viewModel.setOnsetDate(LocalDate(2026, 11, 5))
        viewModel.setOnsetTime(LocalTime(23, 30))
        viewModel.setWakeDate(LocalDate(2026, 11, 6))
        viewModel.setWakeTime(LocalTime(7, 15))

        viewModel.save()

        assertTrue(viewModel.state.value.isSaved)
        val saved = repository.records.value.single()
        assertEquals(Instant.parse("2026-11-06T04:30:00Z"), saved.sleepOnset)
        assertEquals(Instant.parse("2026-11-06T12:15:00Z"), saved.wakeTime)
        assertEquals(SleepSource.MANUAL, saved.source)
    }

    @Test
    fun `a rejected entry shows the problem until the next edit`() {
        val viewModel = viewModel(isNap = false)
        viewModel.setWakeDate(LocalDate(2026, 11, 7))

        viewModel.save()

        assertFalse(viewModel.state.value.isSaved)
        assertEquals(SleepEntryProblem.IN_FUTURE, viewModel.state.value.problem)
        assertTrue(repository.records.value.isEmpty())

        viewModel.setWakeDate(LocalDate(2026, 11, 6))
        assertNull(viewModel.state.value.problem)
    }
}
