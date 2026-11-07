package com.circapin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.circapin.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime

private const val MINUTES_PER_HOUR = 60

/** Times and dates in the device's own zone and format. Storage stays in UTC. */
internal fun Instant.formattedTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(toJavaInstant())

internal fun Instant.formattedDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(toJavaInstant())

internal fun LocalTime.formatted(): String = toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

internal fun LocalDate.formatted(): String = toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Hours and minutes, such as "2 h 30 min", "2 h" or "40 min". The sign is dropped. */
@Composable
internal fun durationText(minutes: Int): String {
    val hours = abs(minutes) / MINUTES_PER_HOUR
    val rest = abs(minutes) % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, rest)
        rest == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, rest)
    }
}
