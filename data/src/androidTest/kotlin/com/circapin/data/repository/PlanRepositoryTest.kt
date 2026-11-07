package com.circapin.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.data.local.CircaPinDatabase
import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.GoalReason
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.NapWindow
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNote
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.entities.TimeWindow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanRepositoryTest {
    private lateinit var db: CircaPinDatabase
    private lateinit var goals: GoalRepositoryImpl
    private lateinit var plans: PlanRepositoryImpl
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val date = LocalDate(2026, 11, 10)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CircaPinDatabase::class.java,
        ).build()
        goals = GoalRepositoryImpl(db.scheduleGoalDao())
        plans = PlanRepositoryImpl(db.dailyPlanDao(), db.energyForecastDao(), db.eatingWindowDao(), db.sleepScoreDao())
    }

    @After
    fun tearDown() = db.close()

    private fun goal(wake: LocalTime = LocalTime(7, 0)) = ScheduleGoal(
        reason = GoalReason.INTERNSHIP,
        targetWakeTime = wake,
        targetDate = LocalDate(2027, 6, 1),
        createdAt = now,
    )

    private fun plan(goalId: Long, nap: NapWindow? = null) = DailyPlan(
        date = date,
        goalId = goalId,
        brightLight = TimeWindow(now, now + 2.hours),
        avoidLight = TimeWindow(now + 11.hours, now + 14.hours),
        caffeineCutoff = now + 6.hours,
        targetBedtime = now + 14.hours,
        nap = nap,
    )

    @Test
    fun aGoalSurvivesARoundTrip() = runTest {
        assertNull(goals.observeActive().first())

        val id = goals.setActive(goal())

        assertEquals(goal().copy(id = id), goals.observeActive().first())
    }

    @Test
    fun aNewGoalAbandonsThePreviousOne() = runTest {
        val first = goals.setActive(goal())
        val second = goals.setActive(goal(wake = LocalTime(8, 0)))

        assertEquals(second, goals.observeActive().first()?.id)
        val statuses = db.scheduleGoalDao().observeAll().first().associate { it.id to it.status }
        assertEquals(GoalStatus.ABANDONED, statuses[first])
        assertEquals(GoalStatus.ACTIVE, statuses[second])
    }

    @Test
    fun aDaySurvivesARoundTripWithItsNap() = runTest {
        val goalId = goals.setActive(goal())
        val nap = NapWindow(TimeWindow(now + 6.hours, now + 8.hours), 20.minutes)
        val eating = EatingWindow(date, now + 30.minutes, now + 11.hours)
        val forecast = EnergyForecast(date, List(24) { it / 24f })

        plans.saveDay(plan(goalId, nap), eating, forecast)

        val day = plans.observeDay(date).first()!!
        assertEquals(plan(goalId, nap), day.plan)
        assertEquals(eating, day.eatingWindow)
        assertEquals(forecast, day.forecast)
        assertEquals(plan(goalId, nap), plans.getPlan(date))
    }

    @Test
    fun aDayWithoutANapKeepsItsNapEmpty() = runTest {
        val goalId = goals.setActive(goal())

        plans.saveDay(plan(goalId), EatingWindow(date, now, now + 10.hours), EnergyForecast(date, List(24) { 0.5f }))

        assertNull(plans.getPlan(date)?.nap)
    }

    @Test
    fun aScoreKeepsItsMissingPartsAndNote() = runTest {
        val score = SleepScore(date, 72, 75, null, 90, null, SleepScoreNote(SleepScoreNoteKind.SHORT, 120))

        plans.saveScore(score)

        assertEquals(score, plans.observeScore(date).first())
    }
}
