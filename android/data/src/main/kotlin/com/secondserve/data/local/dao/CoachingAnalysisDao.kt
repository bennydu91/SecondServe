package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachingAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CoachingAnalysisEntity): Long

    @Query("SELECT * FROM coaching_analyses WHERE session_id = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: Long): CoachingAnalysisEntity?

    @Query("SELECT * FROM coaching_analyses WHERE session_id = :sessionId LIMIT 1")
    fun observeBySessionId(sessionId: Long): Flow<CoachingAnalysisEntity?>
}
