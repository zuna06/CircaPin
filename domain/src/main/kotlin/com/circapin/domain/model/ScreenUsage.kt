package com.circapin.domain.model

/** A device-wide screen transition; app switches don't double count overlapping activity. */
data class ScreenTransition(val atMillis: Long, val active: Boolean)

object ScreenUsage {
    fun minutes(events: List<ScreenTransition>, from: Long, until: Long): Int {
        if (until <= from) return 0
        var active = false
        var cursor = from
        var total = 0L
        events.sortedBy { it.atMillis }.forEach { event ->
            if (event.atMillis > until) return@forEach
            val end = event.atMillis.coerceAtLeast(from)
            if (active) total += (end - cursor).coerceAtLeast(0)
            cursor = end
            active = event.active
        }
        if (active) total += (until - cursor).coerceAtLeast(0)
        return (total / 60_000).toInt()
    }
}
