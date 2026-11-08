package com.circapin.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.NapWindow
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNote
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.entities.TimeWindow
import com.circapin.domain.model.GoalProgress
import com.circapin.ui.components.formatted
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-11-10T14:00:00Z")
    private val goal = ScheduleGoal(id = 1, targetWakeTime = LocalTime(7, 0), targetDate = LocalDate(2026, 11, 13), createdAt = now)
    private val model = ModelState(now + 10.hours, 2.5f, 1f, 1f, now)
    private val plan = DailyPlan(
        date = LocalDate(2026, 11, 10),
        goalId = 1,
        brightLight = TimeWindow(now, now + 2.hours),
        avoidLight = TimeWindow(now + 9.hours, now + 12.hours),
        caffeineCutoff = now + 4.hours,
        targetBedtime = now + 12.hours,
        nap = NapWindow(TimeWindow(now + 6.hours, now + 8.hours), 20.minutes),
    )
    private val forecast = EnergyForecast(plan.date, List(24) { hour -> if (hour == 17) 0.9f else if (hour == 4) 0.1f else 0.5f })

    private fun show(state: HomeUiState, onEditGoal: () -> Unit = {}) {
        compose.setContent { HomeScreen(state = state, now = now, onEditGoal = onEditGoal) }
    }

    private fun full(progress: GoalProgress = GoalProgress(LocalTime(0, 0), LocalTime(21, 0), 180, 3, 60, 0)) = HomeUiState(
        loading = false,
        goal = goal,
        progress = progress,
        model = model,
        day = DayPlan(plan, EatingWindow(plan.date, now, now + 9.hours), forecast),
        score = SleepScore(plan.date, 74, 100, 67, null, 90, SleepScoreNote(SleepScoreNoteKind.LATE, 40)),
    )

    @Test
    fun withoutAGoalTheScreenAsksForOne() {
        var asked = false
        show(HomeUiState(loading = false, model = model), onEditGoal = { asked = true })

        compose.onNodeWithText("Tell CircaPin", substring = true).assertIsDisplayed()
        // The card's title and its button share the text.
        compose.onAllNodesWithText("Set a goal").assertCountEquals(2)
        compose.onNodeWithText("Preparing", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Set a goal")[1].performClick()

        assertTrue(asked)
    }

    @Test
    fun everyPlanItemIsShown() {
        show(full())

        listOf("Seek bright light", "Avoid bright light", "Last caffeine", "Nap", "Meals", "Bedtime").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(", up to 20 min", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBodyClockIsComparedWithTheGoal() {
        show(full())

        compose.onNodeWithText("Your body clock is 3 h behind your goal.", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Right now your body thinks it's", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 nights left", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnreachableGoalIsFlagged() {
        show(full(GoalProgress(LocalTime(2, 0), LocalTime(21, 0), 300, 3, 60, 120)))

        compose.onNodeWithText("At a safe pace you'll still be about 2 h off", substring = true).assertIsDisplayed()
    }

    @Test
    fun theScoreExplainsWhatHeldItBack() {
        show(full())

        compose.onNodeWithContentDescription("Sleep score 74 out of 100. Good, but you went to bed 40 min late").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theForecastHasATextAlternative() {
        show(full())

        // Built with the app's formatter: Android puts a narrow no-break space before AM and PM.
        val fivePm = LocalTime(17, 0).formatted()
        val fourAm = LocalTime(4, 0).formatted()

        compose.onNodeWithText("Most alert around $fivePm, least alert around $fourAm.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show hourly values").performScrollTo().performClick()
        compose.onNodeWithText("$fivePm: 90%").performScrollTo().assertIsDisplayed()
    }
}
