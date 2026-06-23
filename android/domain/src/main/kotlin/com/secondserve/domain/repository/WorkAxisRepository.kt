package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.AxisSuggestion
import com.secondserve.domain.model.WorkAxis
import kotlinx.coroutines.flow.Flow

interface WorkAxisRepository {
    fun getWorkAxes(): Flow<List<WorkAxis>>
    suspend fun createWorkAxis(title: String): AppResult<Unit>
    suspend fun updateWorkAxis(id: Long, title: String): AppResult<Unit>
    suspend fun deleteWorkAxis(id: Long): AppResult<Unit>
    suspend fun getActiveWorkAxesTitles(): List<String>
    fun observePendingSuggestions(): Flow<List<AxisSuggestion>>
    suspend fun hasPendingSuggestions(): Boolean
    suspend fun generateAndSaveSuggestions(): AppResult<Unit>
    suspend fun acceptSuggestion(id: Long): AppResult<Unit>
    suspend fun ignoreSuggestion(id: Long)
}
