package com.circapin.domain.repository

import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.SleepScore
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PlanRepository {
    fun observeDay(date: LocalDate): Flow<DayPlan?>

    fun observeScore(date: LocalDate): Flow<SleepScore?>

    suspend fun getPlan(date: LocalDate): DailyPlan?

    /** Stores a day's plan, eating window and forecast together. */
    suspend fun saveDay(plan: DailyPlan, eatingWindow: EatingWindow, forecast: EnergyForecast)

    suspend fun saveScore(score: SleepScore)
    suspend fun deleteScore(date: LocalDate)

    suspend fun saveForecast(forecast: EnergyForecast)

    fun observeScoresSince(from: LocalDate): Flow<List<SleepScore>>
}
