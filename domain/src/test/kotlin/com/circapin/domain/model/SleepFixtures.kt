package com.circapin.domain.model

import com.circapin.domain.entities.SleepRecord
import com.circapin.domain.entities.SleepSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Fixtures in local time, so tests read like a sleep diary. */
internal object SleepFixtures {
    val UTC = TimeZone.UTC

    /** A night that starts on the evening of [date]. Onsets before noon fall on the following calendar date. */
    fun night(
        date: LocalDate,
        onset: LocalTime,
        length: Duration = 8.hours,
        source: SleepSource = SleepSource.MANUAL,
        timeZone: TimeZone = UTC,
    ): SleepRecord {
        val onsetDate = if (onset.hour < 12) date.plusDays(1) else date
        val start = LocalDateTime(onsetDate, onset).toInstant(timeZone)
        return SleepRecord(sleepOnset = start, wakeTime = start + length, isNap = false, source = source)
    }

    /** A night starting [onsetMinutes] after midnight at the start of [date], so 1500 is 1:00 AM the next day. */
    fun nightAt(date: LocalDate, onsetMinutes: Int, length: Duration = 8.hours): SleepRecord {
        val start = LocalDateTime(date, LocalTime(0, 0)).toInstant(UTC) + onsetMinutes.minutes
        return SleepRecord(sleepOnset = start, wakeTime = start + length, isNap = false, source = SleepSource.MANUAL)
    }

    fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

    /** Minutes after noon-to-noon start of the evening, so 11 PM is 1380 and 1 AM is 1500. */
    fun eveningMinutes(time: LocalTime): Int = (time.hour * 60 + time.minute).let { if (it < 12 * 60) it + 24 * 60 else it }
}
