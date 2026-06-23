package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.CoachingSynthesisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachingSynthesisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CoachingSynthesisEntity): Long

    @Query("SELECT * FROM coaching_syntheses ORDER BY generated_at DESC LIMIT 1")
    suspend fun getLatest(): CoachingSynthesisEntity?

    @Query("SELECT * FROM coaching_syntheses ORDER BY generated_at DESC LIMIT 1")
    fun observeLatest(): Flow<CoachingSynthesisEntity?>
}
