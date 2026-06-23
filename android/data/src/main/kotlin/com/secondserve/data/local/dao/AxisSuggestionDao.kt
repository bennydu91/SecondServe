package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.AxisSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AxisSuggestionDao {
    @Query("SELECT * FROM axis_suggestions WHERE status = 'PENDING' ORDER BY generated_at DESC")
    fun observePending(): Flow<List<AxisSuggestionEntity>>

    @Query("SELECT COUNT(*) FROM axis_suggestions WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT * FROM axis_suggestions WHERE id = :id")
    suspend fun getById(id: Long): AxisSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AxisSuggestionEntity>)

    @Query("UPDATE axis_suggestions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}
