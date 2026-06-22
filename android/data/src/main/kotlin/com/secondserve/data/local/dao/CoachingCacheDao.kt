package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.CoachingCacheEntity

@Dao
interface CoachingCacheDao {
    @Query("SELECT * FROM coaching_cache WHERE match_id = :matchId AND pattern = :pattern LIMIT 1")
    suspend fun getEntry(matchId: Long, pattern: String): CoachingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: CoachingCacheEntity)

    @Query("UPDATE coaching_cache SET is_stale = 1 WHERE match_id = :matchId")
    suspend fun markAllStale(matchId: Long)

    @Query("SELECT * FROM coaching_cache WHERE match_id = :matchId")
    suspend fun getAllForMatch(matchId: Long): List<CoachingCacheEntity>
}
