package com.circapin.data.repository

import com.circapin.data.local.DailyPlanDao
import com.circapin.data.local.EatingWindowDao
import com.circapin.data.local.EnergyForecastDao
import com.circapin.data.local.SleepScoreDao
import com.circapin.data.local.toDomain
import com.circapin.data.local.toEntity
import com.circapin.domain.entities.DailyPlan
import com.circapin.domain.entities.DayPlan
import com.circapin.domain.entities.EatingWindow
import com.circapin.domain.entities.EnergyForecast
import com.circapin.domain.entities.SleepScore
import com.circapin.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class PlanRepositoryImpl(
    private val plans: DailyPlanDao,
    private val forecasts: EnergyForecastDao,
    private val eatingWindows: EatingWindowDao,
    private val scores: SleepScoreDao,
) : PlanRepository {
    override fun observeDay(date: LocalDate): Flow<DayPlan?> =
        combine(plans.observe(date), eatingWindows.observe(date), forecasts.observe(date)) { plan, eating, forecast ->
            plan?.let { DayPlan(it.toDomain(), eating?.toDomain(), forecast?.toDomain()) }
        }

    override fun observeScore(date: LocalDate): Flow<SleepScore?> = scores.observe(date).map { it?.toDomain() }

    override suspend fun getPlan(date: LocalDate): DailyPlan? = plans.get(date)?.toDomain()

    override suspend fun saveDay(plan: DailyPlan, eatingWindow: EatingWindow, forecast: EnergyForecast) {
        plans.saveDay(plan.toEntity(), eatingWindow.toEntity(), forecast.toEntity())
    }

    override suspend fun saveScore(score: SleepScore) {
        scores.upsert(score.toEntity())
    }
}
