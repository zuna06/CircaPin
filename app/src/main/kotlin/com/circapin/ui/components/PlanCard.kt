package com.circapin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.circapin.R
import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.TimeWindow

/** Today's actions, each with its time. Each row reads as one item in TalkBack. */
@Composable
fun PlanCard(plan: DailyPlan, eatingWindow: EatingWindow?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.plan_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            PlanRow(stringResource(R.string.plan_bright_light), plan.brightLight.formatted())
            PlanRow(stringResource(R.string.plan_avoid_light), plan.avoidLight.formatted())
            PlanRow(stringResource(R.string.plan_caffeine), stringResource(R.string.plan_by, plan.caffeineCutoff.formattedTime()))
            plan.nap?.let { nap ->
                PlanRow(
                    stringResource(R.string.plan_nap),
                    stringResource(R.string.plan_nap_value, nap.window.formatted(), nap.maxDuration.inWholeMinutes.toInt()),
                )
            }
            eatingWindow?.let {
                PlanRow(stringResource(R.string.plan_meals), TimeWindow(it.firstMeal, it.lastMeal).formatted())
            }
            PlanRow(stringResource(R.string.plan_bedtime), plan.targetBedtime.formattedTime(), emphasised = true)
        }
    }
}

@Composable
private fun PlanRow(label: String, value: String, emphasised: Boolean = false) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
        val valueStyle = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        if (maxWidth >= 300.dp && scale <= 1.2f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(value, style = valueStyle, fontWeight = if (emphasised) FontWeight.SemiBold else null, modifier = Modifier.weight(0.58f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = valueStyle, fontWeight = if (emphasised) FontWeight.SemiBold else null)
            }
        }
    }
}

@Composable
private fun TimeWindow.formatted(): String =
    stringResource(R.string.sleep_time_range, start.formattedTime(), end.formattedTime())
