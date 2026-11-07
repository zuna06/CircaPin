package com.circapin

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import com.circapin.domain.entities.UserProfile
import com.circapin.domain.repository.UserProfileRepository
import com.circapin.domain.usecases.UpdateModelUseCase
import com.circapin.ui.FakeModelRepository
import com.circapin.ui.FakeSleepRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelUpdaterTest {
    private val now = Instant.parse("2026-11-06T20:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@ModelUpdaterTest.now
    }

    private class FakeProfiles : UserProfileRepository {
        val profile = MutableStateFlow<UserProfile?>(null)
        override fun observeProfile(): Flow<UserProfile?> = profile
        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
    }

    private val sleep = FakeSleepRepository()
    private val profiles = FakeProfiles()
    private val models = FakeModelRepository()
    private val updater = ModelUpdater(sleep, profiles, UpdateModelUseCase(models, clock)) { TimeZone.UTC }

    private fun TestScope.started() = updater.start(backgroundScope)

    @Test
    fun `nothing is computed before onboarding`() = runTest(UnconfinedTestDispatcher()) {
        started()

        sleep.add(SleepRecord(sleepOnset = now - 20.hours, wakeTime = now - 12.hours, isNap = false, source = SleepSource.MANUAL))

        assertTrue(models.saved.isEmpty())
    }

    @Test
    fun `the model runs after onboarding and again on each new record`() = runTest(UnconfinedTestDispatcher()) {
        started()
        profiles.profile.value = UserProfile(chronotypeScore = 14, onboardingComplete = true)
        assertEquals(1, models.saved.size)
        assertEquals(0f, models.saved.last().confidence)

        sleep.add(SleepRecord(sleepOnset = now - 20.hours, wakeTime = now - 12.hours, isNap = false, source = SleepSource.MANUAL))

        assertEquals(2, models.saved.size)
        assertTrue(models.saved.last().confidence > 0f)
        assertEquals(now, models.saved.last().lastUpdated)
    }
}
