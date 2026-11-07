package com.circapin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Fixed hourly points with shaded estimated sleep hours and an adjacent accessible hourly list. */
@Composable
fun EnergyGraph(hourlyAlertness: List<Float>, description: String, modifier: Modifier = Modifier, sleepHours: Set<Int> = emptySet()) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val sleep = MaterialTheme.colorScheme.secondaryContainer
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
            val step = size.width / 23
            sleepHours.forEach { hour ->
                val left = ((hour - 0.5f) * step).coerceAtLeast(0f)
                val right = ((hour + 0.5f) * step).coerceAtMost(size.width)
                drawRect(sleep, Offset(left, 0f), Size(right - left, size.height))
            }
            listOf(0f, 0.5f, 1f).forEach { level ->
                drawLine(grid, Offset(0f, level * size.height), Offset(size.width, level * size.height),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            val path = Path()
            hourlyAlertness.take(24).forEachIndexed { hour, value ->
                val x = hour * step
                val y = (1f - value.coerceIn(0f, 1f)) * size.height
                if (hour == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color, 2.dp.toPx(), Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
