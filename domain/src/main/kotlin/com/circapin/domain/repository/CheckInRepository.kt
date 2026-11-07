package com.circapin.domain.repository

import com.circapin.domain.entities.CheckIn
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CheckInRepository {
    fun observeAll(): Flow<List<CheckIn>>

    suspend fun get(date: LocalDate): CheckIn?

    suspend fun save(checkIn: CheckIn)
}
