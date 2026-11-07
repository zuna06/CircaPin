package com.circapin.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.*
import com.circapin.domain.model.CircadianModel
import com.circapin.domain.usecases.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelPerformanceTest {
    @Test fun thirtyDayModelFitsTheComputationBudgetAfterWarmup() {
        val now = Clock.System.now()
        val records = (0 until 30).map { day -> SleepRecord(sleepOnset = now - day.days - 8.hours,
            wakeTime = now - day.days, isNap = false, source = SleepSource.MANUAL) }
        val profile = UserProfile(14, 8f, true)
        repeat(10) { CircadianModel.compute(records, profile, now, TimeZone.UTC) }
        val times = (0 until 50).map {
            val start = System.nanoTime()
            CircadianModel.compute(records, profile, now, TimeZone.UTC)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val p95 = times[47]
        Log.i("CircaPinPerformance", "30-day model: median=${times[25]}ms p95=${p95}ms")
        assertTrue("Model p95 ${p95}ms exceeds 200ms on this emulator", p95 < 200)
    }
}
