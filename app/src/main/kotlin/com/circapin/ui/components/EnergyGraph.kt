package com.circapin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState

private const val PERCENT = 100
private const val HOURS_BETWEEN_LABELS = 3

/**
 * The hour-by-hour alertness curve, as a percentage for each hour from midnight.
 * The chart is one TalkBack node described by [description]; the hourly list beside it is the text alternative.
 */
@Composable
fun EnergyGraph(hourlyAlertness: List<Float>, description: String, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hourlyAlertness) {
        producer.runTransaction { lineModel { series(hourlyAlertness.map { it * PERCENT }) } }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { HOURS_BETWEEN_LABELS }),
            ),
        ),
        modelProducer = producer,
        // The whole day fits the card, so the afternoon isn't hidden off to the side.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    )
}
