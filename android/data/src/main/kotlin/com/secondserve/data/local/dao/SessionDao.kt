package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.secondserve.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT scorer, COUNT(*) as cnt FROM points WHERE session_id = :sessionId GROUP BY scorer")
    suspend fun getPointCountsByScorer(sessionId: Long): List<ScorerCount>

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND updated_at > :afterMs")
    suspend fun countCompletedSince(afterMs: Long): Int

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND updated_at > :afterMs ORDER BY updated_at DESC")
    suspend fun getCompletedSince(afterMs: Long): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sessions WHERE scheduled_at IS NOT NULL AND status = 'PLANNED' ORDER BY scheduled_at ASC")
    suspend fun getPlannedSessions(): List<SessionEntity>
}
