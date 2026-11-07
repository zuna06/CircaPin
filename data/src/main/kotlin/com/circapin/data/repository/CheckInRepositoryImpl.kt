package com.circapin.data.repository

import com.circapin.data.local.CheckInDao
import com.circapin.data.local.CheckInEntity
import com.circapin.domain.entities.CheckIn
import com.circapin.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class CheckInRepositoryImpl(private val dao: CheckInDao) : CheckInRepository {

    override fun observeAll(): Flow<List<CheckIn>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(date: LocalDate): CheckIn? = dao.get(date)?.toDomain()

    override suspend fun save(checkIn: CheckIn) = dao.upsert(checkIn.toEntity())
}

private fun CheckInEntity.toDomain() = CheckIn(
    date = date,
    alertness = reportedAlertness,
    predictedAlertness = predictedAlertness,
    appliedCorrectionMinutes = appliedCorrectionMinutes,
)

private fun CheckIn.toEntity() = CheckInEntity(
    date = date,
    reportedAlertness = alertness,
    predictedAlertness = predictedAlertness,
    delta = (alertness - 1) / 4f - predictedAlertness,
    appliedCorrectionMinutes = appliedCorrectionMinutes,
)
