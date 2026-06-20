package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPending(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("UPDATE sync_queue SET status = 'FAILED', retry_count = retry_count + 1 WHERE id = :id")
    suspend fun markFailed(id: Long)
}
