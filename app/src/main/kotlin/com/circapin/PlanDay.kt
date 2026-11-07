package com.circapin

import com.circapin.domain.usecases.RefreshTodayUseCase
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone

/** Screens follow the 4 AM plan boundary and system time-zone changes while remaining open. */
fun planDays(clock: Clock, zones: Provider<TimeZone>) = flow {
    while (true) {
        val zone = zones.get()
        emit(RefreshTodayUseCase.planDate(clock.now(), zone) to zone)
        delay(60_000)
    }
}.distinctUntilChanged()
