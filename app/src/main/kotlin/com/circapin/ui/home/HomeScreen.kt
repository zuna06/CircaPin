package com.circapin.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.circapin.R
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.GoalStatus
import com.circapin.domain.entities.ModelState
import com.circapin.domain.entities.ScheduleGoal
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.entities.SleepScoreNoteKind
import com.circapin.domain.model.GoalProgress
import com.circapin.domain.model.ShiftPlanner
import com.circapin.ui.components.EnergyGraph
import com.circapin.ui.components.PlanCard
import com.circapin.ui.components.durationText
import com.circapin.ui.components.formatted
import com.circapin.ui.components.formattedTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime

@Composable
fun HomeRoute(
    onEditGoal: () -> Unit,
    onProgress: () -> Unit = {},
    onCheckIn: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, now = Clock.System.now(), onEditGoal = onEditGoal, onProgress = onProgress, onCheckIn = onCheckIn)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, now: Instant, onEditGoal: () -> Unit, onProgress: () -> Unit = {}, onCheckIn: () -> Unit = {}) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_today)) }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.loading) return@Column

            if (state.todayCheckIn == null && state.model != null) {
                CheckInPromptCard(onCheckIn)
            }

            val goal = state.goal
            if (state.goalStatus == GoalStatus.COMPLETED) {
                GoalCompletedCard(onEditGoal)
            } else if (state.goalStatus == GoalStatus.MAINTAINED) {
                GoalMaintainedCard(onEditGoal)
            }

            if (goal == null) {
                NoGoalCard(onEditGoal)
            } else if (state.goalStatus == GoalStatus.ACTIVE) {
                GoalCard(goal, state.progress, onEditGoal, onProgress)
            }
            state.progress?.let { ShortfallCard(it, goal) }
            state.model?.let { BodyClockCard(it, state.progress, now) }
            state.score?.let { ScoreCard(it) }
            val day = state.day
            when {
                day != null -> PlanCard(day.plan, day.eatingWindow)
                goal != null -> Text(stringResource(R.string.plan_preparing))
            }
            day?.forecast?.let { ForecastCard(it) }
        }
    }
}

@Composable
private fun NoGoalCard(onEditGoal: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_no_goal_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_no_goal_body))
            Button(onClick = onEditGoal) { Text(stringResource(R.string.home_no_goal_title)) }
        }
    }
}

@Composable
private fun GoalCard(goal: ScheduleGoal, progress: GoalProgress?, onEditGoal: () -> Unit, onProgress: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}) {
                    Text(
                        text = stringResource(R.string.home_goal, goal.targetWakeTime.formatted(), goal.targetDate.formatted()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    progress?.let {
                        Text(
                            text = if (it.nightsLeft > 0) {
                                stringResource(R.string.home_nights_left, pluralStringResource(R.plurals.nights, it.nightsLeft, it.nightsLeft))
                            } else {
                                stringResource(R.string.home_goal_date_reached)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                TextButton(onClick = onEditGoal) { Text(stringResource(R.string.action_change_goal)) }
            }
            TextButton(onClick = onProgress) { Text(stringResource(R.string.progress_title)) }
        }
    }
}

@Composable
private fun CheckInPromptCard(onCheckIn: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.checkin_question), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onCheckIn) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@Composable
private fun GoalCompletedCard(onEditGoal: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.goal_completed_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.goal_completed_body))
            Button(onClick = onEditGoal) { Text(stringResource(R.string.home_no_goal_title)) }
        }
    }
}

@Composable
private fun GoalMaintainedCard(onEditGoal: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.goal_maintained), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onEditGoal) { Text(stringResource(R.string.home_no_goal_title)) }
        }
    }
}

/** Shown when the goal can't be reached at the safe pace. */
@Composable
private fun ShortfallCard(progress: GoalProgress, goal: ScheduleGoal?) {
    if (progress.shortfallMinutes <= 0 || goal == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (progress.nightsLeft > 0) {
                stringResource(R.string.home_shortfall, durationText(progress.shortfallMinutes), goal.targetDate.formatted())
            } else {
                stringResource(R.string.home_shortfall_after, durationText(progress.shortfallMinutes))
            },
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun BodyClockCard(model: ModelState, progress: GoalProgress?, now: Instant) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.body_clock_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.body_clock_dlmo, model.estimatedDlmo.formattedTime()))
            progress?.let {
                // shiftMinutes is how much earlier the clock must move, so positive means running behind.
                val offset = it.shiftMinutes
                Text(
                    when {
                        abs(offset) <= ShiftPlanner.ON_TARGET_MINUTES -> stringResource(R.string.body_clock_on_schedule)
                        offset > 0 -> stringResource(R.string.body_clock_behind, durationText(offset))
                        else -> stringResource(R.string.body_clock_ahead, durationText(offset))
                    },
                )
                if (abs(offset) > ShiftPlanner.ON_TARGET_MINUTES) {
                    Text(stringResource(R.string.body_clock_now, (now - offset.minutes).formattedTime()))
                }
            }
            Text(stringResource(R.string.body_clock_debt_and_jetlag, model.sleepDebtHours, model.socialJetlagHours))
            Text(
                text = if (model.confidence == 0f) {
                    stringResource(R.string.body_clock_questionnaire_only)
                } else {
                    stringResource(R.string.body_clock_confidence, (model.confidence * 100).roundToInt())
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScoreCard(score: SleepScore) {
    val rating = stringResource(
        when {
            score.overall >= 85 -> R.string.score_great
            score.overall >= 70 -> R.string.score_good
            score.overall >= 50 -> R.string.score_fair
            else -> R.string.score_poor
        },
    )
    val minutes = durationText(score.note.minutes)
    val note = when (score.note.kind) {
        SleepScoreNoteKind.ON_TRACK -> null
        SleepScoreNoteKind.SHORT -> stringResource(R.string.score_note_short, minutes)
        SleepScoreNoteKind.LATE -> stringResource(R.string.score_note_late, minutes)
        SleepScoreNoteKind.EARLY -> stringResource(R.string.score_note_early, minutes)
        SleepScoreNoteKind.BROKEN -> stringResource(R.string.score_note_broken)
        SleepScoreNoteKind.IRREGULAR -> stringResource(R.string.score_note_irregular, minutes)
    }
    val label = note?.let { stringResource(R.string.score_with_note, rating, it) }
        ?: stringResource(R.string.score_on_track, rating)
    val description = stringResource(R.string.score_description, score.overall, label)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            Text(score.overall.toString(), style = MaterialTheme.typography.displaySmall)
            Column {
                Text(stringResource(R.string.score_title), style = MaterialTheme.typography.titleMedium)
                Text(label)
            }
        }
    }
}

@Composable
private fun ForecastCard(forecast: EnergyForecast) {
    var showValues by rememberSaveable { mutableStateOf(false) }
    val hours = forecast.hourlyAlertness
    val peak = hours.indices.maxBy { hours[it] }
    val low = hours.indices.minBy { hours[it] }
    val summary = stringResource(R.string.forecast_summary, LocalTime(peak, 0).formatted(), LocalTime(low, 0).formatted())

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.forecast_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(summary)
            EnergyGraph(
                hourlyAlertness = hours,
                description = summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Text(stringResource(R.string.forecast_note), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showValues = !showValues }) {
                Text(stringResource(if (showValues) R.string.forecast_hide_values else R.string.forecast_show_values))
            }
            if (showValues) {
                hours.forEachIndexed { hour, value ->
                    Text(stringResource(R.string.forecast_hour_value, LocalTime(hour, 0).formatted(), (value * 100).roundToInt()))
                }
            }
        }
    }
}
